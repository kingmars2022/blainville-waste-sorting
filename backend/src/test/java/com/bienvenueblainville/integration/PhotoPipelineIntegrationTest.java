package com.bienvenueblainville.integration;

import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.bienvenueblainville.photo.lambda.PhotoProcessingHandler;
import com.bienvenueblainville.support.LocalS3;
import com.bienvenueblainville.support.PhotoFixtures;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.GpsDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * The photo path end to end against a real S3 API: sign, upload, process, serve.
 *
 * <p>The only thing simulated here is the event delivery — S3 calling Lambda
 * is AWS's job, so the handler is invoked directly with the notification S3
 * would have sent. Everything either side of that is real: the signature is
 * produced by the AWS SDK, the upload is an ordinary HTTP PUT from an HTTP
 * client that knows nothing about this application, and the handler reads and
 * writes through the real S3 client.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class PhotoPipelineIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.photo.endpoint", LocalS3::endpoint);
        registry.add("app.photo.bucket", () -> LocalS3.BUCKET);
        registry.add("app.photo.region", () -> "us-east-1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private S3Client s3;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearTheSharedQuota() {
        // Every endpoint here counts against the assistant's per-IP quota, and
        // the counter lives in real Redis keyed by the hour - so it survives
        // between runs, and a suite run twice inside an hour used to start
        // failing with 429s that had nothing to do with the code under test.
        Set<String> keys = redis.keys("assistant:ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void aResidentUploadsStraightToS3AndOnlyTheStrippedCopyIsServed() throws Exception {
        byte[] photo = PhotoFixtures.jpegWithGps(2400, 1800);

        // 1. Ask for a ticket. The bytes have not touched this application.
        JsonNode ticket = objectMapper.readTree(mockMvc.perform(post("/api/photos/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentType", "image/jpeg",
                                "contentLength", photo.length))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String photoId = ticket.get("photoId").asText();
        String uploadUrl = ticket.get("uploadUrl").asText();

        // 2. Upload with a plain HTTP client, as a browser would. Nothing here
        //    knows about Spring, the application, or any AWS credential.
        HttpResponse<Void> upload = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(uploadUrl))
                        .header("Content-Type", "image/jpeg")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(photo))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(upload.statusCode()).isEqualTo(200);

        // 3. Before the Lambda runs there is nothing to serve - the original is
        //    never reachable, which is the entire privacy design.
        mockMvc.perform(get("/api/photos/" + photoId)).andExpect(status().isNotFound());

        // 4. The event S3 would have sent.
        new PhotoProcessingHandler(s3).handleRequest(objectCreated("original/" + photoId), null);

        // 5. Now it is served, and the coordinates are gone.
        byte[] served = mockMvc.perform(get("/api/photos/" + photoId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(ImageMetadataReader.readMetadata(new ByteArrayInputStream(photo))
                .getDirectoriesOfType(GpsDirectory.class)).isNotEmpty();
        assertThat(ImageMetadataReader.readMetadata(new ByteArrayInputStream(served))
                .getDirectoriesOfType(GpsDirectory.class)).isEmpty();
        assertThat(served.length).isLessThan(photo.length);
    }

    @Test
    void theOriginalStaysInTheBucketAndIsNeverServed() throws Exception {
        // Kept for reprocessing, not exposed. The route only ever reads the
        // processed prefix, so there is no code path that returns these bytes.
        byte[] photo = PhotoFixtures.jpegWithGps(800, 600);
        String photoId = uploadAndProcess(photo);

        assertThat(s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(LocalS3.BUCKET).key("original/" + photoId).build()).asByteArray())
                .isEqualTo(photo);
    }

    @Test
    void anAnswerAskedForBeforeTheLambdaHasRunWaitsForItInsteadOfSayingNotFound() throws Exception {
        // The race this closes: the browser uploads and asks for an answer
        // straight away, but S3 triggers the Lambda asynchronously. Nothing
        // waited, so the first read found nothing and the resident was told
        // the photo did not exist. Every other test here invokes the handler
        // before asking, which is exactly why none of them saw it.
        String photoId = uploadWithoutProcessing();

        // The Lambda finishes a second and a half in, while the request is
        // already waiting.
        ScheduledExecutorService lambda = Executors.newSingleThreadScheduledExecutor();
        lambda.schedule(
                () -> new PhotoProcessingHandler(s3).handleRequest(objectCreated("original/" + photoId), null),
                1500, TimeUnit.MILLISECONDS);

        long startedAt = System.nanoTime();
        try {
            // No Anthropic key is configured here, so identification itself
            // cannot succeed — and that is fine, because what is under test is
            // everything before it. The assertion is that the request did NOT
            // come back "still being prepared": it waited for the Lambda
            // rather than giving the resident an answer about a photo that was
            // a second from ready.
            mockMvc.perform(post("/api/photos/" + photoId + "/identify").param("language", "fr"))
                    .andExpect(header().doesNotExist("Retry-After"));
        } finally {
            lambda.shutdownNow();
        }

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                .as("the request waited for the Lambda instead of failing immediately")
                .isGreaterThan(Duration.ofMillis(1400));

        // And the processed copy really is there now, written by the Lambda
        // rather than by anything the request did.
        mockMvc.perform(get("/api/photos/" + photoId)).andExpect(status().isOk());
    }

    @Test
    void anUploadedButUnprocessedPhotoIsNotReportedAsMissing() throws Exception {
        // Nothing will ever process this one, so the wait runs out. "Not
        // found" would be a lie about a photo the resident watched upload;
        // 503 says try again, which is what the browser then does.
        String photoId = uploadWithoutProcessing();

        mockMvc.perform(post("/api/photos/" + photoId + "/identify").param("language", "fr"))
                .andExpect(status().isServiceUnavailable())
                // Retry-After is what separates this from the other 503 this
                // endpoint can return — a missing API key, which retrying
                // cannot fix. The browser keys its retry on the header.
                .andExpect(header().string("Retry-After", "2"));
    }

    @Test
    void aPhotoIdThatWasNeverUploadedIsStillNotFound() throws Exception {
        mockMvc.perform(post("/api/photos/" + UUID.randomUUID() + "/identify").param("language", "fr"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theLambdaIgnoresItsOwnOutput() {
        // A function triggered by a prefix it also writes to re-triggers itself,
        // and on a metered service that bills until someone notices.
        S3Event ownOutput = objectCreated("processed/11111111-2222-3333-4444-555555555555");

        String result = new PhotoProcessingHandler(s3).handleRequest(ownOutput, null);

        assertThat(result).isEqualTo("processed 0");
    }

    @Test
    void refusesAFileTypeItWillNotProcess() throws Exception {
        // Rejected before a signature exists, so the application never issues
        // permission to upload something it cannot handle.
        mockMvc.perform(post("/api/photos/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentType", "application/pdf", "contentLength", 1000))))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void refusesAPhotoLargerThanTheLimit() throws Exception {
        mockMvc.perform(post("/api/photos/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentType", "image/jpeg", "contentLength", 50_000_000L))))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void rejectsAPhotoIdThatIsNotOne() throws Exception {
        mockMvc.perform(get("/api/photos/../../etc/passwd")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/photos/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void theSizeAndTypeAreSignedIntoTheUrlRatherThanOnlyCheckedHere() throws Exception {
        // A limit enforced only by this application is a limit a caller can
        // skip by not calling it - the presigned URL goes straight to S3.
        // Putting content-length and content-type in the signature means S3
        // itself rejects a request that does not match.
        //
        // Note what this asserts and what it cannot: that the constraints are
        // part of the signature. Whether the server refuses a mismatched body
        // is real S3's behaviour, and the local S3 implementation used here
        // does not simulate it, so that half is not verified locally.
        JsonNode ticket = objectMapper.readTree(mockMvc.perform(post("/api/photos/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentType", "image/jpeg", "contentLength", 12345))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        String url = java.net.URLDecoder.decode(
                ticket.get("uploadUrl").asText(), java.nio.charset.StandardCharsets.UTF_8);

        assertThat(url).contains("X-Amz-SignedHeaders=");
        String signedHeaders = url.substring(url.indexOf("X-Amz-SignedHeaders=") + "X-Amz-SignedHeaders=".length());
        signedHeaders = signedHeaders.split("&")[0];

        assertThat(signedHeaders).contains("content-length");
        assertThat(signedHeaders).contains("content-type");
        // And it expires, so a leaked ticket is not a standing upload grant.
        assertThat(url).contains("X-Amz-Expires=300");
    }

    /** Everything up to the Lambda: ticket, PUT, and nothing else. */
    private String uploadWithoutProcessing() throws Exception {
        byte[] photo = PhotoFixtures.jpegWithGps(800, 600);

        JsonNode ticket = objectMapper.readTree(mockMvc.perform(post("/api/photos/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentType", "image/jpeg",
                                "contentLength", photo.length))))
                .andReturn().getResponse().getContentAsString());

        String photoId = ticket.get("photoId").asText();
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(ticket.get("uploadUrl").asText()))
                        .header("Content-Type", "image/jpeg")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(photo))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        return photoId;
    }

    private String uploadAndProcess(byte[] photo) throws Exception {
        JsonNode ticket = objectMapper.readTree(mockMvc.perform(post("/api/photos/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "contentType", "image/jpeg",
                                "contentLength", photo.length))))
                .andReturn().getResponse().getContentAsString());

        String photoId = ticket.get("photoId").asText();
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(ticket.get("uploadUrl").asText()))
                        .header("Content-Type", "image/jpeg")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(photo))
                        .build(),
                HttpResponse.BodyHandlers.discarding());

        new PhotoProcessingHandler(s3).handleRequest(objectCreated("original/" + photoId), null);
        return photoId;
    }

    /** The notification shape S3 actually sends. */
    private static S3Event objectCreated(String key) {
        S3EventNotification.S3BucketEntity bucket =
                new S3EventNotification.S3BucketEntity(LocalS3.BUCKET, null, null);
        S3EventNotification.S3ObjectEntity object =
                new S3EventNotification.S3ObjectEntity(key, 0L, null, null, null);
        S3EventNotification.S3Entity entity =
                new S3EventNotification.S3Entity(null, bucket, object, "1.0");

        return new S3Event(List.of(new S3EventNotification.S3EventNotificationRecord(
                "us-east-1", "ObjectCreated:Put", "aws:s3", null, "2.1",
                null, null, entity, null)));
    }
}

package com.bienvenueblainville.photo;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;

/**
 * S3, and specifically the presigned upload.
 *
 * <p><b>Why the bytes do not come through this application.</b> A phone photo
 * is a few megabytes. Accepting it as a multipart POST means every upload
 * occupies a request thread and a chunk of heap for as long as the resident's
 * mobile connection takes — the slowest clients cost the most, which is the
 * wrong way round. A presigned PUT means the browser sends the bytes straight
 * to S3 and the application only ever handles the signature.
 *
 * <p>The signature is where the constraints live, and they are not advisory:
 * the content type and an exact content length are signed into the URL, so a
 * caller who uploads something else, or something bigger, gets a rejection
 * from S3 rather than from a check this code hopes it remembered to write.
 */
@Component
public class PhotoStorage {
    private final S3Client s3;
    private final S3Presigner presigner;
    private final PhotoProperties properties;

    public PhotoStorage(S3Client s3, S3Presigner presigner, PhotoProperties properties) {
        this.s3 = s3;
        this.presigner = presigner;
        this.properties = properties;
    }

    /**
     * @param contentLength signed in, so the limit is enforced by S3 rather
     *                      than by trusting the client's declared size
     */
    public String presignUpload(String photoId, String contentType, long contentLength) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(PhotoProperties.ORIGINAL_PREFIX + photoId)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(properties.uploadTtl()))
                        .putObjectRequest(put)
                        .build())
                .url()
                .toString();
    }

    /** Short enough that a fast Lambda is not made to look slow. */
    private static final Duration POLL_INTERVAL = Duration.ofMillis(300);

    /** The processed copy: EXIF stripped and resized, written by the Lambda. */
    public Optional<byte[]> readProcessed(String photoId) {
        return read(PhotoProperties.PROCESSED_PREFIX + photoId);
    }

    /**
     * The processed copy, waiting for it if the Lambda has not finished.
     *
     * <p>The bug this exists for: the browser uploads to S3 and asks for an
     * answer immediately, but the Lambda is triggered <em>asynchronously</em>
     * by the object creation. Nothing in between waited, so the first read
     * almost always found nothing and the resident was told their photo did
     * not exist — for a photo that would be ready a second later. The
     * integration test never saw it because it invokes the handler
     * synchronously.
     *
     * <p>Polling rather than anything cleverer because S3 has nothing to
     * subscribe to, and a bounded wait on a request thread is honest at this
     * scale: one resident, one photo, a few seconds at most.
     */
    public Optional<byte[]> awaitProcessed(String photoId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (true) {
            Optional<byte[]> processed = readProcessed(photoId);
            if (processed.isPresent() || System.nanoTime() >= deadline) {
                return processed;
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }

    public Optional<byte[]> readOriginal(String photoId) {
        return read(PhotoProperties.ORIGINAL_PREFIX + photoId);
    }

    private Optional<byte[]> read(String key) {
        try {
            ResponseBytes<?> object = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            return Optional.of(object.asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}

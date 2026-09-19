package com.bienvenueblainville.photo.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * The Lambda: S3 object created under {@code original/} → stripped, resized
 * copy under {@code processed/}.
 *
 * <p>Two prefixes in one bucket rather than one prefix and an in-place
 * overwrite, because a Lambda that writes back into the prefix it is triggered
 * by re-triggers itself. That mistake is cheap to make, and on a metered
 * service it bills until someone notices.
 *
 * <p>The handler is deliberately thin: it decodes the event, moves bytes, and
 * delegates every decision to {@link PhotoProcessor}, which knows nothing
 * about AWS and can therefore be tested as a plain function.
 */
public class PhotoProcessingHandler implements RequestHandler<S3Event, String> {
    private final S3Client s3;

    /** Used by the Lambda runtime, which needs a no-argument constructor. */
    @SuppressWarnings("unused")
    public PhotoProcessingHandler() {
        this(S3Client.create());
    }

    public PhotoProcessingHandler(S3Client s3) {
        this.s3 = s3;
    }

    @Override
    public String handleRequest(S3Event event, Context context) {
        int processed = 0;

        for (S3EventNotificationRecord record : event.getRecords()) {
            String bucket = record.getS3().getBucket().getName();
            // S3 event keys arrive URL-encoded: a photo id is safe, but a key
            // with a space or a '+' silently becomes a different key if this
            // is skipped.
            String key = URLDecoder.decode(
                    record.getS3().getObject().getUrlDecodedKey() != null
                            ? record.getS3().getObject().getUrlDecodedKey()
                            : record.getS3().getObject().getKey(),
                    StandardCharsets.UTF_8);

            if (!key.startsWith("original/")) {
                // Ignore anything outside the input prefix, including this
                // function's own output.
                continue;
            }

            try {
                ResponseBytes<?> original = s3.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(bucket).key(key).build());

                byte[] cleaned = PhotoProcessor.process(original.asByteArray());

                s3.putObject(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key("processed/" + key.substring("original/".length()))
                                .contentType("image/jpeg")
                                .build(),
                        RequestBody.fromBytes(cleaned));
                processed++;
            } catch (Exception e) {
                // Rethrown so the invocation fails and S3 retries it. Swallowing
                // it would leave a photo that never gets processed and no signal
                // that anything went wrong.
                throw new IllegalStateException("Failed to process " + key, e);
            }
        }

        return "processed " + processed;
    }
}

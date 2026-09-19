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

    /** The processed copy: EXIF stripped and resized, written by the Lambda. */
    public Optional<byte[]> readProcessed(String photoId) {
        return read(PhotoProperties.PROCESSED_PREFIX + photoId);
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

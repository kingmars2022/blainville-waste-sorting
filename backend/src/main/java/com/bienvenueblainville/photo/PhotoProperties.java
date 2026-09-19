package com.bienvenueblainville.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bucket        where originals and thumbnails live
 * @param region        AWS region
 * @param endpoint      blank in production; set to point the client at a local
 *                      S3 implementation for tests
 * @param uploadTtl     how long a presigned upload URL stays valid, in seconds
 * @param maxUploadBytes largest photo a resident may upload
 */
@ConfigurationProperties(prefix = "app.photo")
public record PhotoProperties(
        String bucket,
        String region,
        String endpoint,
        long uploadTtl,
        long maxUploadBytes
) {
    public static final String ORIGINAL_PREFIX = "original/";
    public static final String PROCESSED_PREFIX = "processed/";
}

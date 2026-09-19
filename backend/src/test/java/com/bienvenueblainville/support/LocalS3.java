package com.bienvenueblainville.support;

import com.adobe.testing.s3mock.S3MockApplication;

import java.util.HashMap;
import java.util.Map;

/**
 * A real S3 API, as a jar rather than a container.
 *
 * <p>Started once for the JVM and shared, in the same way as {@link InMemoryMongo}:
 * it speaks the actual S3 HTTP protocol, so the presigned-URL flow is exercised
 * for real — the signature is produced by the AWS SDK, sent as an ordinary HTTP
 * PUT, and validated on the other side. Nothing about that is mocked, which
 * matters because a presigned URL that is subtly wrong still looks like a URL.
 */
public final class LocalS3 {
    public static final String BUCKET = "blainville-test-photos";

    private static S3MockApplication application;
    private static String endpoint;

    private LocalS3() {
    }

    public static synchronized String endpoint() {
        if (application == null) {
            // A mutable map: S3MockApplication.start removes entries as it
            // reads them, so Map.of throws UnsupportedOperationException.
            Map<String, Object> properties = new HashMap<>();
            properties.put(S3MockApplication.PROP_HTTP_PORT, String.valueOf(S3MockApplication.RANDOM_PORT));
            properties.put(S3MockApplication.PROP_HTTPS_PORT, String.valueOf(S3MockApplication.RANDOM_PORT));
            properties.put(S3MockApplication.PROP_INITIAL_BUCKETS, BUCKET);
            properties.put(S3MockApplication.PROP_SILENT, "true");

            application = S3MockApplication.start(properties);
            endpoint = "http://localhost:" + application.getHttpPort();
            Runtime.getRuntime().addShutdownHook(new Thread(application::stop));
        }
        return endpoint;
    }
}

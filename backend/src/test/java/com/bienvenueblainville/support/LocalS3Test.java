package com.bienvenueblainville.support;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the one thing about the local S3 server that is easy to get wrong.
 *
 * <p>S3Mock is itself a Spring Boot application, so starting it runs Spring
 * Boot's auto-configuration against <em>this</em> project's classpath — which
 * carries a MySQL DataSource and Flyway. Left alone it therefore migrates
 * whatever database {@code application.yml} points at, which looks harmless on
 * a developer machine whose MySQL accepts those credentials and fails the
 * whole photo suite in CI, where the test user is granted only the test
 * database.
 *
 * <p>This test deliberately carries no {@code @Tag("integration")}: it runs in
 * the default {@code mvn test} profile, where no database is reachable at all.
 * If the S3 server ever starts dragging the application's persistence
 * auto-configuration along again, it fails here first — cheaply, and without
 * needing any infrastructure to reproduce.
 */
class LocalS3Test {
    @Test
    void startsAnS3ServerWithoutBootingTheApplicationsDatabase() {
        String endpoint = LocalS3.endpoint();

        assertThat(endpoint).startsWith("http://localhost:");

        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.CA_CENTRAL_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-key", "test-secret")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            // Throws if the server is not actually serving the S3 API.
            client.headBucket(HeadBucketRequest.builder().bucket(LocalS3.BUCKET).build());
        }
    }
}

package com.bienvenueblainville.photo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(PhotoProperties.class)
public class PhotoConfig {

    /**
     * Credentials come from the default chain in production — environment,
     * container role, instance profile — so no AWS key is ever configured in
     * this application or committed to this repository. A local S3
     * implementation is selected by setting an endpoint, and only then are
     * static dummy credentials used, because a local implementation still
     * expects a signature it can parse.
     */
    @Bean
    public S3Client s3Client(PhotoProperties properties) {
        var builder = S3Client.builder().region(Region.of(properties.region()));

        if (hasEndpoint(properties)) {
            builder.endpointOverride(URI.create(properties.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("local", "local")))
                    // Path style: a local endpoint has no per-bucket DNS.
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(PhotoProperties properties) {
        var builder = S3Presigner.builder().region(Region.of(properties.region()));

        if (hasEndpoint(properties)) {
            builder.endpointOverride(URI.create(properties.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("local", "local")))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    private static boolean hasEndpoint(PhotoProperties properties) {
        return properties.endpoint() != null && !properties.endpoint().isBlank();
    }
}

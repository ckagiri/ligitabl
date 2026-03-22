package com.ligitabl.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;

@Configuration
@ConditionalOnProperty(name = "ligitabl.email.provider", havingValue = "aws-ses")
public class AwsSesConfig {

    @Bean
    public SesClient sesClient(
            @Value("${ligitabl.email.aws-ses.region:us-east-1}") String region,
            @Value("${ligitabl.email.aws-ses.access-key:}") String accessKey,
            @Value("${ligitabl.email.aws-ses.secret-key:}") String secretKey) {
        return SesClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(resolveCredentialsProvider(accessKey, secretKey))
                .build();
    }

    private AwsCredentialsProvider resolveCredentialsProvider(String accessKey, String secretKey) {
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        }

        return DefaultCredentialsProvider.create();
    }
}

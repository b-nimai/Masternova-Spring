package com.masternova.storage;

import java.net.URI;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Wires {@link ObjectStorage} into any app with this jar (like {@code MessagingAutoConfiguration}).
 * Creating the clients opens no connection, so apps and tests that never touch storage pay nothing.
 */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  S3Client s3Client(StorageProperties props) {
    var builder =
        S3Client.builder()
            .region(Region.of(props.region()))
            .credentialsProvider(credentials(props))
            .forcePathStyle(props.pathStyle())
            // ⭐ SDK 2.30+ adds CRC checksums to every upload by default. Third-party S3 servers
            //    and — worse — PRESIGNED urls would then demand checksum headers the browser
            //    never sends. Only send them where the S3 API requires them.
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
    if (props.endpoint() != null) {
      builder.endpointOverride(props.endpoint());
    }
    return builder.build();
  }

  @Bean
  @ConditionalOnMissingBean
  ObjectStorage objectStorage(S3Client s3, StorageProperties props) {
    URI publicEndpoint = props.publicEndpoint() != null ? props.publicEndpoint() : props.endpoint();
    return new ObjectStorage(
        s3, presigner(props, publicEndpoint), presigner(props, props.endpoint()), props.bucket());
  }

  private static S3Presigner presigner(StorageProperties props, URI endpoint) {
    var builder =
        S3Presigner.builder()
            .region(Region.of(props.region()))
            .credentialsProvider(credentials(props))
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(props.pathStyle()).build());
    if (endpoint != null) {
      builder.endpointOverride(endpoint);
    }
    return builder.build();
  }

  private static AwsCredentialsProvider credentials(StorageProperties props) {
    return props.hasStaticCredentials()
        ? StaticCredentialsProvider.create(
            AwsBasicCredentials.create(props.accessKey(), props.secretKey()))
        : DefaultCredentialsProvider.builder().build();
  }
}

package com.masternova.storage;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Where the media bucket is, bound from {@code masternova.storage.*}.
 *
 * @param endpoint the S3 API to call; {@code null} means real AWS S3 (the SDK resolves the regional
 *     endpoint). SeaweedFS in dev: {@code http://localhost:9010} (ADR-0012).
 * @param publicEndpoint the host put into PRESIGNED urls, when the browser reaches storage by a
 *     different name than the server does (compose: the api calls {@code seaweedfs:8333}, the
 *     browser {@code localhost:9010}). {@code null} = same as {@code endpoint}.
 * @param pathStyle {@code http://host/bucket/key} instead of {@code http://bucket.host/key};
 *     required by SeaweedFS (no wildcard DNS), off for AWS
 * @param accessKey static credentials; when blank, the SDK's default chain (env, IRSA, instance
 *     profile) is used — the production path
 */
@Validated
@ConfigurationProperties(prefix = "masternova.storage")
public record StorageProperties(
    URI endpoint,
    URI publicEndpoint,
    @DefaultValue("us-east-1") @NotBlank String region,
    @DefaultValue("masternova-media") @NotBlank String bucket,
    @DefaultValue("false") boolean pathStyle,
    String accessKey,
    String secretKey) {

  boolean hasStaticCredentials() {
    return accessKey != null && !accessKey.isBlank();
  }
}

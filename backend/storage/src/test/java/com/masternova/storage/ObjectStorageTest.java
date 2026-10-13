package com.masternova.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.Part;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** ⭐ The Adapter's translation, unit-tested over a stubbed SDK client: no Spring, no container. */
@SuppressWarnings("unchecked")
class ObjectStorageTest {

  private final S3Client s3 = mock(S3Client.class);
  private final ObjectStorage storage =
      new ObjectStorage(s3, mock(S3Presigner.class), mock(S3Presigner.class), "bucket");

  static S3Exception s3Error(int status, String code) {
    return (S3Exception)
        S3Exception.builder()
            .statusCode(status)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
            .build();
  }

  @Test
  void serverErrorsAndThrottlingAreTemporary() {
    when(s3.listParts(any(Consumer.class))).thenThrow(s3Error(503, "SlowDown"));
    assertThatThrownBy(() -> storage.listParts("k", "u"))
        .isInstanceOf(StorageUnavailableException.class);

    assertThat(ObjectStorage.translate("x", s3Error(500, "InternalError"), null))
        .isInstanceOf(StorageUnavailableException.class);
    assertThat(ObjectStorage.translate("x", s3Error(429, "TooManyRequests"), null))
        .isInstanceOf(StorageUnavailableException.class);
  }

  @Test
  void noAnswerAtAllIsTemporary() {
    when(s3.listParts(any(Consumer.class)))
        .thenThrow(SdkClientException.create("Connection refused"));
    assertThatThrownBy(() -> storage.listParts("k", "u"))
        .isInstanceOf(StorageUnavailableException.class);
  }

  @Test
  void aForgottenUploadIdHasItsOwnException() {
    when(s3.listParts(any(Consumer.class))).thenThrow(s3Error(404, "NoSuchUpload"));
    assertThatThrownBy(() -> storage.listParts("k", "upload-1"))
        .isInstanceOf(NoSuchUploadException.class)
        .hasMessageContaining("upload-1");
  }

  @Test
  void clientErrorsArePermanent() {
    StorageException e = ObjectStorage.translate("x", s3Error(403, "AccessDenied"), null);
    assertThat(e).isExactlyInstanceOf(StorageException.class);
  }

  @Test
  void abortingAnUploadTheProviderForgotIsSuccess() {
    when(s3.abortMultipartUpload(any(Consumer.class))).thenThrow(s3Error(404, "NoSuchUpload"));
    assertThatCode(() -> storage.abortMultipartUpload("k", "u")).doesNotThrowAnyException();
  }

  @Test
  void headOfAMissingObjectIsEmptyNotAnError() {
    when(s3.headObject(any(Consumer.class))).thenThrow(s3Error(404, null));
    assertThat(storage.head("missing")).isEmpty();
  }

  @Test
  void listPartsFollowsEveryPage() {
    when(s3.listParts(any(Consumer.class)))
        .thenReturn(
            ListPartsResponse.builder()
                .parts(Part.builder().partNumber(1).size(5L).eTag("a").build())
                .isTruncated(true)
                .nextPartNumberMarker(1)
                .build(),
            ListPartsResponse.builder()
                .parts(Part.builder().partNumber(2).size(3L).eTag("b").build())
                .isTruncated(false)
                .build());

    assertThat(storage.listParts("k", "u"))
        .containsExactly(new StoredPart(1, 5, "a"), new StoredPart(2, 3, "b"));
  }
}

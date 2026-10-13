package com.masternova.patterns.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.masternova.patterns.state.UploadLifecycle.Rejected;
import com.masternova.patterns.state.UploadLifecycle.Step;
import com.masternova.patterns.state.UploadLifecycle.Upload;
import org.junit.jupiter.api.Test;

class UploadLifecycleTest {

  @Test
  void aRetriedCompleteReplaysWithoutASecondSideEffect() {
    Upload upload = new Upload();

    assertThat(upload.complete()).isEqualTo(Step.PERFORM);
    assertThat(upload.complete()).isEqualTo(Step.REPLAY);
    assertThat(upload.complete()).isEqualTo(Step.REPLAY);

    assertThat(upload.pipelinesStarted()).isEqualTo(1);
  }

  @Test
  void theRejectionNamesTheStateNotTheEvent() {
    Upload upload = new Upload();
    upload.abort();

    assertThatThrownBy(upload::complete)
        .isInstanceOfSatisfying(Rejected.class, e -> assertThat(e.code).isEqualTo("UPLOAD_ABORTED"));
    assertThat(upload.abort()).isEqualTo(Step.REPLAY);
  }

  @Test
  void aCompletedUploadCantBeAborted() {
    Upload upload = new Upload();
    upload.complete();

    assertThatThrownBy(upload::abort)
        .isInstanceOfSatisfying(
            Rejected.class, e -> assertThat(e.code).isEqualTo("UPLOAD_ALREADY_COMPLETED"));
  }
}

package com.masternova.patterns.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class JobTest {

  @Test
  void aSubclassListingThePlaylistFirstStillPublishesItLast() {
    Job rendition =
        new Job() {
          @Override
          protected List<Output> produce() {
            return List.of(new Output("index.m3u8", true), new Output("seg_0.m4s", false));
          }
        };

    rendition.run();

    assertThat(rendition.published).containsExactly("seg_0.m4s", "index.m3u8");
    assertThat(rendition.scratchExists).isFalse();
  }

  @Test
  void cleanupRunsEvenWhenTheStepThrows() {
    Job broken =
        new Job() {
          @Override
          protected List<Output> produce() {
            throw new IllegalStateException("ffmpeg died");
          }
        };

    assertThatThrownBy(broken::run).hasMessage("ffmpeg died");
    assertThat(broken.scratchExists).isFalse();
    assertThat(broken.published).isEmpty();
  }
}

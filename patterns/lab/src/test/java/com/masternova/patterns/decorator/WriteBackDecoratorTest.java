package com.masternova.patterns.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WriteBackDecoratorTest {

  private final InMemoryPositionStore db = new InMemoryPositionStore();
  private final WriteBackPositionStore store = new WriteBackPositionStore(db); // ⭐ the wrapping

  @Test
  void manyRecordsAreOneWriteAtTheFlush() {
    for (int second = 0; second <= 300; second += 15) {
      store.record("u1:l1", second);
      store.record("u2:l1", second / 2);
    }
    assertThat(db.writes()).isZero();

    store.flush();

    assertThat(db.writes()).isEqualTo(1);
    assertThat(db.find("u1:l1")).contains(300);
  }

  @Test
  void readsSeeTheBufferBeforeItIsFlushed() {
    store.record("u1:l1", 120);
    store.flush();
    store.record("u1:l1", 135);

    assertThat(store.find("u1:l1")).contains(135);
    assertThat(db.find("u1:l1")).contains(120);
  }

  @Test
  void aBatchWrittenTwiceChangesNothingBecauseTheMergeIsIdempotent() {
    db.recordAll(Map.of("u1:l1", 200));
    db.recordAll(Map.of("u1:l1", 200)); // a crashed flush, replayed
    store.record("u1:l1", 50); // a late, older heartbeat

    store.flush();

    assertThat(store.find("u1:l1")).contains(200); // max never goes down
  }
}

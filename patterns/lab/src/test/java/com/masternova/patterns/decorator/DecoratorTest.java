package com.masternova.patterns.decorator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DecoratorTest {

  private final InMemoryGrantRepository db = new InMemoryGrantRepository();
  private final GrantRepository repository = new CachingGrantRepository(db); // ⭐ the wrapping

  @Test
  void callersSeeTheSameInterfaceAndTheDatabaseIsAskedOnce() {
    db.save(new Grant("u1", "c1", Optional.empty()));

    assertThat(repository.find("u1", "c1")).isPresent();
    assertThat(repository.find("u1", "c1")).isPresent();
    assertThat(db.reads()).isEqualTo(1);
  }

  @Test
  void aMissingRowIsCachedToo() {
    assertThat(repository.find("stranger", "c1")).isEmpty();
    assertThat(repository.find("stranger", "c1")).isEmpty();
    assertThat(db.reads()).isEqualTo(1);
  }

  @Test
  void aWriteThroughTheDecoratorEvictsSoTheNextReadIsFresh() {
    assertThat(repository.find("u1", "c1")).isEmpty(); // "no row" cached

    repository.save(new Grant("u1", "c1", Optional.empty()));

    assertThat(repository.find("u1", "c1")).isPresent();
  }

  @Test
  void aWriteThatBypassesTheDecoratorIsWhyEveryWriterMustBeOnTheInterface() {
    assertThat(repository.find("u1", "c1")).isEmpty();
    db.save(new Grant("u1", "c1", Optional.empty())); // ❌ behind the cache's back

    assertThat(repository.find("u1", "c1")).isEmpty(); // stale: in Redis, until the TTL
  }
}

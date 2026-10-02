package com.masternova.patterns.proxy;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * <b>Virtual proxy</b> — stands in for the expensive object and creates it only on first use. A
 * course page lists 40 lectures; most learners open one or two, so loading 40 manifests up front
 * would waste 38 storage round-trips.
 *
 * <p>Same idea as Hibernate's lazy-loading proxies for {@code @ManyToOne(fetch = LAZY)}.
 */
public final class LazyVideoManifest implements VideoManifest {

  private final Supplier<VideoManifest> loader;
  private VideoManifest real; // created on demand

  public LazyVideoManifest(Supplier<VideoManifest> loader) {
    this.loader = Objects.requireNonNull(loader, "loader");
  }

  @Override
  public synchronized String playlist() { // synchronized: two threads must not both load it
    if (real == null) {
      real = loader.get();
    }
    return real.playlist();
  }
}

package com.masternova.patterns.proxy;

/** <b>Subject</b> — the interface both the real object and its proxies implement. */
public interface VideoManifest {

  /** The HLS playlist for a lecture video (expensive: storage round-trip + signing). */
  String playlist();
}

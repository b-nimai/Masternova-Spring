package com.masternova.patterns.proxy;

import java.util.concurrent.atomic.AtomicInteger;

/** <b>RealSubject</b> — loads the playlist from storage the moment it is constructed. */
public final class RemoteVideoManifest implements VideoManifest {

  /** How many times anything was actually loaded — lets the tests see the proxy's effect. */
  public static final AtomicInteger LOADS = new AtomicInteger();

  private final String playlist;

  public RemoteVideoManifest(String lectureId) {
    LOADS.incrementAndGet(); // imagine: GET from S3 + sign 40 segment URLs
    this.playlist = "#EXTM3U\n# lecture " + lectureId + "\n720p.m3u8\n1080p.m3u8";
  }

  @Override
  public String playlist() {
    return playlist;
  }
}

package com.masternova.storage;

import java.util.UUID;

/**
 * ⭐ The bucket's key layout — the contract between the api (writes the source) and the worker
 * (writes HLS). Every key is a FUNCTION of ids, never random and never stored, which is what makes
 * a crashed job's rerun OVERWRITE its output instead of duplicating it (video-pipeline LLD §3,
 * invariant 2).
 *
 * <pre>
 * media/{assetId}/source
 * media/{assetId}/hls/master.m3u8              ← written last of all: the asset's commit marker
 * media/{assetId}/hls/{rung}/index.m3u8        ← written last of its rung
 * media/{assetId}/hls/{rung}/init.mp4
 * media/{assetId}/hls/{rung}/seg_00000.m4s …
 * media/{assetId}/poster.jpg · sprite.jpg · sprite.vtt
 * </pre>
 */
public final class MediaKeys {

  public static final String PLAYLIST = "index.m3u8";
  public static final String INIT_SEGMENT = "init.mp4";

  /** {@code ffmpeg -hls_segment_filename} pattern; {@link #segment} formats the same names. */
  public static final String SEGMENT_PATTERN = "seg_%05d.m4s";

  private MediaKeys() {}

  public static String asset(UUID assetId) {
    return "media/" + assetId + "/";
  }

  public static String source(UUID assetId) {
    return asset(assetId) + "source";
  }

  public static String hls(UUID assetId) {
    return asset(assetId) + "hls/";
  }

  public static String master(UUID assetId) {
    return hls(assetId) + "master.m3u8";
  }

  public static String rung(UUID assetId, String rung) {
    if (!rung.matches("[0-9]{2,4}p")) {
      throw new IllegalArgumentException("rung must look like 720p: " + rung);
    }
    return hls(assetId) + rung + "/";
  }

  public static String rungPlaylist(UUID assetId, String rung) {
    return rung(assetId, rung) + PLAYLIST;
  }

  public static String segment(UUID assetId, String rung, int number) {
    return rung(assetId, rung) + SEGMENT_PATTERN.formatted(number);
  }

  public static String poster(UUID assetId) {
    return asset(assetId) + "poster.jpg";
  }

  public static String sprite(UUID assetId) {
    return asset(assetId) + "sprite.jpg";
  }

  public static String spriteVtt(UUID assetId) {
    return asset(assetId) + "sprite.vtt";
  }
}

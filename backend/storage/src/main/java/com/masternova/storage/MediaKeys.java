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

  /** Everything media lives under (the orphan sweeper lists this prefix). */
  public static final String ROOT = "media/";

  public static final String PLAYLIST = "index.m3u8";
  public static final String MASTER = "master.m3u8";
  public static final String POSTER = "poster.jpg";
  public static final String SPRITE = "sprite.jpg";
  public static final String SPRITE_VTT = "sprite.vtt";

  /**
   * The smallest rung (and source) height the pipeline makes: a rung is named {@code <height>p},
   * and a name must look like one. ⭐ One constant for the ladder, the probe and the key layout
   * (they disagreed once: a 2-px "rung" passed the ladder and failed only at upload).
   */
  public static final int MIN_RUNG_HEIGHT = 16;

  /** Redis pub/sub: the worker publishes an asset's progress here, every api replica listens. */
  public static final String PROGRESS_CHANNELS = "media:asset:*";

  public static final String INIT_SEGMENT = "init.mp4";

  /** {@code ffmpeg -hls_segment_filename} pattern; {@link #segment} formats the same names. */
  public static final String SEGMENT_PATTERN = "seg_%05d.m4s";

  private MediaKeys() {}

  public static String asset(UUID assetId) {
    return ROOT + assetId + "/";
  }

  public static String source(UUID assetId) {
    return asset(assetId) + "source";
  }

  public static String hls(UUID assetId) {
    return asset(assetId) + "hls/";
  }

  public static String master(UUID assetId) {
    return hls(assetId) + MASTER;
  }

  public static String rung(UUID assetId, String rung) {
    if (!isRungName(rung)) {
      throw new IllegalArgumentException("rung must look like 720p: " + rung);
    }
    return hls(assetId) + rung + "/";
  }

  /** {@code 16p} … {@code 9999p}: a height of at least {@link #MIN_RUNG_HEIGHT}, then {@code p}. */
  public static boolean isRungName(String rung) {
    return rung.matches("[0-9]{2,4}p")
        && Integer.parseInt(rung.substring(0, rung.length() - 1)) >= MIN_RUNG_HEIGHT;
  }

  public static String rungPlaylist(UUID assetId, String rung) {
    return rung(assetId, rung) + PLAYLIST;
  }

  public static String segment(UUID assetId, String rung, int number) {
    return rung(assetId, rung) + SEGMENT_PATTERN.formatted(number);
  }

  public static String progressChannel(UUID assetId) {
    return "media:asset:" + assetId;
  }

  public static String poster(UUID assetId) {
    return asset(assetId) + POSTER;
  }

  public static String sprite(UUID assetId) {
    return asset(assetId) + SPRITE;
  }

  public static String spriteVtt(UUID assetId) {
    return asset(assetId) + SPRITE_VTT;
  }
}

package com.masternova.storage;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The one reading of an HLS playlist's URIs, shared by the worker (PACKAGE checks every referenced
 * file exists) and the api (playback rewrites each one for a viewer) — so what playback signs is
 * exactly what packaging checked. A URI is every non-tag, non-blank line, plus the {@code URI="…"}
 * of {@code #EXT-X-MAP} (the fMP4 init segment).
 */
public final class HlsPlaylists {

  private static final Pattern MAP_URI = Pattern.compile("URI=\"([^\"]+)\"");

  private HlsPlaylists() {}

  /** The playlist with every URI mapped through {@code uri}; tags and blank lines unchanged. */
  public static String rewriteUris(String playlist, UnaryOperator<String> uri) {
    return playlist
            .lines()
            .map(
                line -> {
                  if (line.startsWith("#EXT-X-MAP:")) {
                    Matcher m = MAP_URI.matcher(line);
                    return m.find()
                        ? line.substring(0, m.start(1))
                            + uri.apply(m.group(1))
                            + line.substring(m.end(1))
                        : line;
                  }
                  return line.isBlank() || line.startsWith("#") ? line : uri.apply(line.strip());
                })
            .collect(Collectors.joining("\n"))
        + "\n";
  }

  /** Every URI the playlist references, in order. */
  public static List<String> uris(String playlist) {
    List<String> uris = new java.util.ArrayList<>();
    rewriteUris(
        playlist,
        u -> {
          uris.add(u);
          return u;
        });
    return List.copyOf(uris);
  }
}

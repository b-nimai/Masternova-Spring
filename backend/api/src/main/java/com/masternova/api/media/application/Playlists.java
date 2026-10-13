package com.masternova.api.media.application;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rewrites the stored HLS playlists for one viewer (ADR-0013). Storage holds them with RELATIVE
 * names (deterministic, viewer-independent — the worker wrote them once); each request gets a copy
 * whose URIs carry that viewer's access:
 *
 * <ul>
 *   <li>master → every rung URI gets {@code ?t=<ticket>} (the rung playlists are served by us);
 *   <li>rung → every segment and the {@code EXT-X-MAP} init segment become presigned storage urls
 *       (the bytes come straight from storage, not through the api).
 * </ul>
 */
final class Playlists {

  private static final Pattern MAP_URI = Pattern.compile("URI=\"([^\"]+)\"");

  private Playlists() {}

  /**
   * Every URI line (not a tag, not blank) mapped through {@code uri}; tags are kept as they are.
   */
  static String rewrite(String playlist, UnaryOperator<String> uri) {
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
}

package com.masternova.api.media;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Media's public API for other modules (catalog). ⭐ Catalog ASKS when it decides (attaching an
 * asset to a lecture, the publish gate) instead of keeping a copy of the status that could be stale
 * at exactly that moment (media LLD §4).
 */
public interface MediaApi {

  /** May {@code userId} attach this asset? Unknown ids are simply {@code false}. */
  boolean isOwnedBy(UUID assetId, UUID userId);

  /** Readiness of each KNOWN asset; unknown ids are absent from the map. One query. */
  Map<UUID, AssetReadiness> readiness(Collection<UUID> assetIds);

  /**
   * A READY asset's master playlist, each rung URI passed through {@code rungUri} (the caller adds
   * its own credential). ⭐ No access check: the caller has made its own (entitlement's token).
   */
  String masterPlaylist(UUID assetId, UnaryOperator<String> rungUri);

  /**
   * One rung's playlist with every segment a presigned storage url, valid for {@code urlTtl}.
   * Throws {@code IllegalArgumentException} for a rung name that isn't one (no {@code ../}).
   */
  String rungPlaylist(UUID assetId, String rung, Duration urlTtl);
}

package com.masternova.api.media;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

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
}

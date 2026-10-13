package com.masternova.api.media.application;

import com.masternova.api.media.AssetReadiness;
import com.masternova.api.media.MediaApi;
import com.masternova.api.media.domain.MediaAsset;
import com.masternova.api.media.domain.MediaAssetRepository;
import com.masternova.api.media.domain.MediaStatus;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link MediaApi} — the read side other modules use. */
@Service
@Transactional(readOnly = true)
class MediaQueries implements MediaApi {

  private final MediaAssetRepository assets;

  MediaQueries(MediaAssetRepository assets) {
    this.assets = assets;
  }

  @Override
  public boolean isOwnedBy(UUID assetId, UUID userId) {
    return assets.findById(assetId).map(a -> a.isOwnedBy(userId)).orElse(false);
  }

  @Override
  public Map<UUID, AssetReadiness> readiness(Collection<UUID> assetIds) {
    if (assetIds.isEmpty()) {
      return Map.of();
    }
    return assets.findByIdIn(assetIds).stream()
        .collect(
            Collectors.toUnmodifiableMap(
                MediaAsset::id,
                a -> new AssetReadiness(a.status() == MediaStatus.READY, a.durationSeconds())));
  }
}

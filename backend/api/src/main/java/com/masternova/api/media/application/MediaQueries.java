package com.masternova.api.media.application;

import com.masternova.api.media.AssetReadiness;
import com.masternova.api.media.MediaApi;
import com.masternova.api.media.domain.MediaAsset;
import com.masternova.api.media.domain.MediaAssetRepository;
import com.masternova.api.media.domain.MediaStatus;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link MediaApi} — the read side other modules use. ⭐ Transactions per method: the playlist
 * methods touch storage only, and must not borrow a database connection to do it.
 */
@Service
class MediaQueries implements MediaApi {

  private final MediaAssetRepository assets;
  private final HlsDelivery hls;

  MediaQueries(MediaAssetRepository assets, HlsDelivery hls) {
    this.assets = assets;
    this.hls = hls;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isOwnedBy(UUID assetId, UUID userId) {
    return assets.findById(assetId).map(a -> a.isOwnedBy(userId)).orElse(false);
  }

  @Override
  @Transactional(readOnly = true)
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

  @Override
  public String masterPlaylist(UUID assetId, UnaryOperator<String> rungUri) {
    return hls.master(assetId, rungUri);
  }

  @Override
  public String rungPlaylist(UUID assetId, String rung, Duration urlTtl) {
    return hls.rung(assetId, rung, urlTtl);
  }
}

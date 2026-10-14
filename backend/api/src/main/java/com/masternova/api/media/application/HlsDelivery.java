package com.masternova.api.media.application;

import com.masternova.storage.HlsPlaylists;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.ObjectStorage;
import java.time.Duration;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * An asset's HLS playlists, rewritten for one viewer (ADR-0013): the master's rung URIs carry the
 * caller's credential; a rung's segments become presigned storage urls, so the bytes never pass
 * through the api. ONE implementation behind both playback paths — the owner's preview ({@link
 * PlaybackService}) and, through {@code MediaApi}, entitlement's learner playback (ADR-0017).
 *
 * <p>No database, no transaction: a storage read and a few hundred local HMAC signatures.
 */
@Component
class HlsDelivery {

  private final ObjectStorage storage;

  HlsDelivery(ObjectStorage storage) {
    this.storage = storage;
  }

  String master(UUID assetId, UnaryOperator<String> rungUri) {
    return HlsPlaylists.rewriteUris(storage.getText(MediaKeys.master(assetId)), rungUri);
  }

  String rung(UUID assetId, String rung, Duration urlTtl) {
    String prefix = MediaKeys.rung(assetId, rung); // validates the rung name (no ../)
    return HlsPlaylists.rewriteUris(
        storage.getText(prefix + MediaKeys.PLAYLIST),
        file -> storage.presignDownload(prefix + file, urlTtl).toString());
  }
}

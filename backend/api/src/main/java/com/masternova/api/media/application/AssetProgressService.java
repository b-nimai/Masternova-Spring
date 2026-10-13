package com.masternova.api.media.application;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.media.domain.MediaAsset;
import com.masternova.api.media.domain.MediaAssetRepository;
import com.masternova.api.media.domain.PipelineJobs;
import com.masternova.api.platform.NotFoundException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An asset's processing progress (API conventions §9): a one-shot snapshot for clients that poll,
 * and a {@link Feed} for the SSE stream.
 *
 * <p>⭐ The access check runs ONCE, when the feed is opened, in the caller's request. The feed's
 * later refreshes run on the Redis listener's thread, where there is no request and no security
 * context — so they read without re-checking, for the asset the caller was allowed to see.
 */
@Service
public class AssetProgressService {

  private final MediaAssetRepository assets;
  private final PipelineJobs jobs;
  private final TransactionTemplate readOnly;

  AssetProgressService(
      MediaAssetRepository assets, PipelineJobs jobs, PlatformTransactionManager transactions) {
    this.assets = assets;
    this.jobs = jobs;
    // programmatic: the feed's refresh runs on the Redis listener's thread, outside any proxy
    this.readOnly = new TransactionTemplate(transactions);
    this.readOnly.setReadOnly(true);
  }

  /** The current snapshot and how to get the next one. */
  public record Feed(AssetProgressView initial, Supplier<AssetProgressView> refresh) {}

  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  public AssetProgressView snapshot(CurrentUser me, UUID assetId) {
    return open(me, assetId).initial();
  }

  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  public Feed open(CurrentUser me, UUID assetId) {
    AssetProgressView initial =
        readOnly.execute(
            status -> {
              MediaAsset asset =
                  assets
                      .findById(assetId)
                      .filter(a -> a.isVisibleTo(me.id(), me.has(Role.ADMIN)))
                      .orElseThrow(() -> new NotFoundException("Asset", assetId));
              return view(asset);
            });
    return new Feed(
        initial, () -> readOnly.execute(status -> view(assets.findById(assetId).orElseThrow())));
  }

  private AssetProgressView view(MediaAsset asset) {
    var progress = jobs.progressOf(asset.id());
    return new AssetProgressView(
        asset.id(),
        asset.status(),
        asset.failureCode(),
        AssetProgressView.overall(asset.status(), progress),
        progress);
  }
}

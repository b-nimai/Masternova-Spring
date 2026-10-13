package com.masternova.api.media.application;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.Role;
import com.masternova.api.media.MediaUploaded;
import com.masternova.api.media.domain.MediaAsset;
import com.masternova.api.media.domain.MediaAssetRepository;
import com.masternova.api.media.domain.PartPlan;
import com.masternova.api.media.domain.PartPlan.PartCheck;
import com.masternova.api.media.domain.UploadSession;
import com.masternova.api.media.domain.UploadSessionRepository;
import com.masternova.api.media.domain.UploadState.Step;
import com.masternova.api.platform.ConflictException;
import com.masternova.api.platform.EventPublisher;
import com.masternova.api.platform.NotFoundException;
import com.masternova.api.platform.RuleViolationException;
import com.masternova.storage.MediaKeys;
import com.masternova.storage.NoSuchUploadException;
import com.masternova.storage.ObjectStorage;
import com.masternova.storage.StorageException;
import com.masternova.storage.StoredPart;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The resumable upload's use cases (media LLD §5): create, resume, complete, abort. The bytes never
 * pass through here — the browser PUTs them to presigned urls; this service plans, asks the
 * provider what arrived (ADR-0014) and records the outcome.
 *
 * <p>Who may: the asset's owner, or an admin. Anyone else gets the same 404 as for a missing id.
 */
@Service
public class UploadService {

  private final UploadSessionRepository sessions;
  private final MediaAssetRepository assets;
  private final ObjectStorage storage;
  private final UploadViews views;
  private final EventPublisher events;
  private final MediaProperties props;
  private final Clock clock;

  UploadService(
      UploadSessionRepository sessions,
      MediaAssetRepository assets,
      ObjectStorage storage,
      UploadViews views,
      EventPublisher events,
      MediaProperties props,
      Clock clock) {
    this.sessions = sessions;
    this.assets = assets;
    this.storage = storage;
    this.views = views;
    this.events = events;
    this.props = props;
    this.clock = clock;
  }

  /** Plans the parts, opens the provider's multipart upload, hands out every part's url. */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public UploadView create(CurrentUser me, String filename, String contentType, long sizeBytes) {
    String type = contentType.toLowerCase(Locale.ROOT).strip();
    if (!props.allowedContentTypes().contains(type)) {
      throw new RuleViolationException(
          "UNSUPPORTED_MEDIA_TYPE",
          "This file type can't be uploaded as a video.",
          Map.of("contentType", type, "allowed", new TreeSet<>(props.allowedContentTypes())));
    }
    PartPlan plan = PartPlan.forSize(sizeBytes);
    Instant now = clock.instant();
    UUID assetId = UUID.randomUUID();
    String key = MediaKeys.source(assetId);

    // ⭐ the provider first: if the inserts below fail, the open upload is an orphan that the
    //    sweeper aborts (7.6). The other order could leave a session pointing at nothing.
    String providerUploadId = storage.createMultipartUpload(key, type);
    assets.save(MediaAsset.awaitingUpload(assetId, me.id(), filename, type, sizeBytes, now));
    UploadSession session =
        sessions.save(
            UploadSession.open(
                UUID.randomUUID(),
                assetId,
                providerUploadId,
                key,
                plan,
                now,
                now.plus(props.sessionTtl())));
    return views.toSend(session, List.of());
  }

  /**
   * Resume = ask the provider (ADR-0014): the parts still missing, with fresh urls. If the provider
   * no longer has the upload, nothing is left to send — {@link #complete} finds out why.
   */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional(readOnly = true)
  public UploadView resume(CurrentUser me, UUID uploadId) {
    UploadSession session = accessible(sessions.findById(uploadId), me, uploadId);
    session.state(clock.instant()).resume(); // 409 unless OPEN
    try {
      return views.toSend(
          session, storage.listParts(session.objectKey(), session.providerUploadId()));
    } catch (NoSuchUploadException gone) {
      return UploadViews.finished(
          session); // nothing to upload; complete() decides completed vs expired
    }
  }

  /**
   * Checks the provider's parts against the plan, completes, and starts the pipeline. A retried or
   * concurrent complete waits on the row lock, then REPLAYs the first one's answer.
   */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public UploadView complete(CurrentUser me, UUID uploadId) {
    UploadSession session = accessible(sessions.lockById(uploadId), me, uploadId);
    Instant now = clock.instant();
    if (session.state(now).complete() == Step.REPLAY) {
      return UploadViews.finished(session);
    }
    String key = session.objectKey();
    PartPlan plan = session.plan();
    try {
      List<StoredPart> stored = storage.listParts(key, session.providerUploadId());
      PartCheck check = plan.check(stored);
      if (!check.complete()) {
        throw check.toProblem(); // 422: exactly which parts to (re)send
      }
      storage.completeMultipartUpload(key, session.providerUploadId(), stored);
    } catch (NoSuchUploadException gone) {
      // ⭐ the provider forgot the upload id: completed (we crashed before our commit last time)
      //    or aborted. Provider truth again — the object is there with the declared size, or not.
      if (storage.head(key).filter(o -> o.size() == plan.size()).isEmpty()) {
        throw new ConflictException(
            "UPLOAD_EXPIRED", "This upload no longer exists. Start a new one.", Map.of());
      }
    }
    long stored = storage.head(key).orElseThrow(() -> missingAfterComplete(key)).size();
    if (stored != plan.size()) {
      throw new StorageException("completed " + key + " has " + stored + " bytes", null);
    }

    session.markCompleted(now);
    MediaAsset asset = assets.findById(session.assetId()).orElseThrow();
    asset.markUploaded();
    events.publish( // same transaction: the pipeline starts iff this commits
        new MediaUploaded(
            asset.id().toString(), asset.ownerId().toString(), key, asset.sizeBytes()));
    return UploadViews.finished(session);
  }

  /** Gives up an open upload; aborting again (or an expired one) is a no-op 204. */
  @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
  @Transactional
  public void abort(CurrentUser me, UUID uploadId) {
    UploadSession session = accessible(sessions.lockById(uploadId), me, uploadId);
    if (session.state(clock.instant()).abort() == Step.REPLAY) {
      return;
    }
    storage.abortMultipartUpload(session.objectKey(), session.providerUploadId());
    session.markAborted();
    assets.findById(session.assetId()).orElseThrow().markAbandoned();
  }

  // ------------------------------------------------------------------ helpers

  private UploadSession accessible(Optional<UploadSession> found, CurrentUser me, UUID uploadId) {
    return found
        .filter(
            s ->
                me.has(Role.ADMIN)
                    || assets.findById(s.assetId()).map(a -> a.isOwnedBy(me.id())).orElse(false))
        .orElseThrow(() -> new NotFoundException("Upload", uploadId));
  }

  private static StorageException missingAfterComplete(String key) {
    return new StorageException("completed " + key + " but HEAD finds nothing", null);
  }
}

package com.masternova.api.media.application;

import com.masternova.api.media.application.UploadView.PartUrl;
import com.masternova.api.media.domain.PartPlan;
import com.masternova.api.media.domain.PartPlan.PartCheck;
import com.masternova.api.media.domain.UploadSession;
import com.masternova.storage.ObjectStorage;
import com.masternova.storage.StoredPart;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/** Builds the {@link UploadView}s the uploader works from: what to send next, with fresh urls. */
@Component
class UploadViews {

  private final ObjectStorage storage;
  private final MediaProperties props;

  UploadViews(ObjectStorage storage, MediaProperties props) {
    this.storage = storage;
    this.props = props;
  }

  /**
   * ⭐ What's left to send, judged from the PROVIDER's parts: the ones it lacks AND the ones with
   * the wrong size (re-PUTting a part number replaces it), each with a fresh presigned url.
   */
  UploadView toSend(UploadSession session, List<StoredPart> stored) {
    PartPlan plan = session.plan();
    PartCheck check = plan.check(stored);
    TreeSet<Integer> toSend = new TreeSet<>(check.missing());
    toSend.addAll(check.wrongSize());
    long uploaded =
        stored.stream()
            .filter(p -> !toSend.contains(p.number()) && !check.unexpected().contains(p.number()))
            .mapToLong(StoredPart::size)
            .sum();
    List<PartUrl> urls =
        toSend.stream()
            .map(
                n ->
                    new PartUrl(
                        n,
                        storage.presignUploadPart(
                            session.objectKey(),
                            session.providerUploadId(),
                            n,
                            props.partUrlTtl())))
            .toList();
    return new UploadView(
        session.id(),
        session.assetId(),
        session.status(),
        plan,
        uploaded,
        urls,
        session.expiresAt());
  }

  /** Nothing left to send (completed, or the provider holds no open upload any more). */
  static UploadView finished(UploadSession session) {
    return new UploadView(
        session.id(),
        session.assetId(),
        session.status(),
        session.plan(),
        session.plan().size(),
        List.of(),
        session.expiresAt());
  }
}

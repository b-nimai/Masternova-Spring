package com.masternova.worker.notification.template;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-send facts the template doesn't own: the app's public URL, and the recipient's unsubscribe
 * link — present only for opt-out-able categories (a mandatory email has nothing to unsubscribe
 * from).
 */
public record RenderContext(URI webUrl, Optional<URI> unsubscribeUrl) {

  public RenderContext {
    Objects.requireNonNull(webUrl, "webUrl");
    Objects.requireNonNull(unsubscribeUrl, "unsubscribeUrl");
  }
}

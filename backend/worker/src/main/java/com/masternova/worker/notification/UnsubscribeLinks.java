package com.masternova.worker.notification;

import com.masternova.kernel.notification.NotificationCategory;
import com.masternova.kernel.notification.UnsubscribeTokens;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the two unsubscribe links an opt-out-able email carries, both holding the same signed
 * token (kernel {@link UnsubscribeTokens}; the api verifies it):
 *
 * <ul>
 *   <li>{@link Link#page()} — in the footer: opens the Angular page, which POSTs. A link scanner's
 *       GET only loads the page, so it can't unsubscribe anyone.
 *   <li>{@link Link#oneClick()} — in the {@code List-Unsubscribe} header (RFC 8058): Gmail's and
 *       Yahoo's "Unsubscribe" button POSTs {@code List-Unsubscribe=One-Click} to it directly.
 * </ul>
 */
@Component
class UnsubscribeLinks {

  record Link(URI page, URI oneClick) {

    /** RFC 2369 + RFC 8058 headers. Mailbox providers require them for bulk senders. */
    Map<String, String> headers() {
      return Map.of(
          "List-Unsubscribe",
          "<" + oneClick + ">",
          "List-Unsubscribe-Post",
          "List-Unsubscribe=One-Click");
    }
  }

  private final UnsubscribeTokens tokens;
  private final Clock clock;
  private final String webUrl;

  UnsubscribeLinks(NotificationProperties properties, Clock clock) {
    this.tokens = new UnsubscribeTokens(properties.tokenSecret());
    this.clock = clock;
    this.webUrl = properties.webUrl().toString().replaceAll("/+$", "");
  }

  /** Empty for mandatory categories: there is nothing to unsubscribe from. */
  Optional<Link> forRecipient(UUID userId, NotificationCategory category) {
    if (category.mandatory()) {
      return Optional.empty();
    }
    // base64url + '.': already URL-safe, no encoding needed
    String token = tokens.issue(userId, category, clock.instant().plus(UnsubscribeTokens.TTL));
    return Optional.of(
        new Link(
            URI.create(webUrl + "/unsubscribe?token=" + token),
            URI.create(webUrl + "/api/v1/notifications/unsubscribe/one-click?token=" + token)));
  }
}

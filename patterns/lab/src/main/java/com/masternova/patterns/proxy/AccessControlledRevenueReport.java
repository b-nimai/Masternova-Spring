package com.masternova.patterns.proxy;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * <b>Protection proxy</b> — same interface, but checks WHO is asking before delegating. An
 * instructor sees only their own revenue; an admin sees anyone's. The real report stays free of
 * security code (single responsibility).
 *
 * <p>Spring Security's {@code @PreAuthorize} is this, generated for you.
 */
public final class AccessControlledRevenueReport implements RevenueReport {

  private final RevenueReport real;
  private final Supplier<Viewer> currentViewer;

  /** Who is asking. */
  public record Viewer(String userId, boolean admin) {}

  public AccessControlledRevenueReport(RevenueReport real, Supplier<Viewer> currentViewer) {
    this.real = Objects.requireNonNull(real, "real");
    this.currentViewer = Objects.requireNonNull(currentViewer, "currentViewer");
  }

  @Override
  public long totalFor(String instructorId) {
    Viewer viewer = currentViewer.get();
    if (!viewer.admin() && !viewer.userId().equals(instructorId)) {
      throw new IllegalStateException(viewer.userId() + " may not see revenue of " + instructorId);
    }
    return real.totalFor(instructorId);
  }
}

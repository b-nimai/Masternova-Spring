package com.masternova.patterns.adapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * ADAPTEE #1 — a "vendor library" we can't change. Its shape is nothing like ours: it wants a raw
 * MIME string, answers with an SMTP reply code, and throws a CHECKED {@link IOException} when the
 * connection drops.
 */
public final class LegacySmtpClient {

  private final Set<String> unknownMailboxes;
  private final List<String> transcript = new ArrayList<>();
  private boolean connectionDown;
  private int counter;

  public LegacySmtpClient(Set<String> unknownMailboxes) {
    this.unknownMailboxes = Set.copyOf(unknownMailboxes);
  }

  public void simulateConnectionDown() {
    connectionDown = true;
  }

  /** @return 250 + queue id on success, 550 when the mailbox does not exist */
  public String sendRaw(String rcptTo, String rawMime) throws IOException {
    if (connectionDown) {
      throw new IOException("connection reset by peer");
    }
    if (unknownMailboxes.contains(rcptTo)) {
      return "550 5.1.1 mailbox unavailable";
    }
    transcript.add(rawMime);
    return "250 2.0.0 queued as Q" + (++counter);
  }

  public List<String> transcript() {
    return List.copyOf(transcript);
  }
}

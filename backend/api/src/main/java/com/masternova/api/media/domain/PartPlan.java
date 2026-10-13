package com.masternova.api.media.domain;

import com.masternova.api.platform.RuleViolationException;
import com.masternova.storage.StoredPart;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * How a file of {@code size} bytes is cut into presigned parts (media LLD §3), and the check of
 * what storage actually holds against it (ADR-0014).
 *
 * <ul>
 *   <li>{@code partSize ≥ 5 MiB}: S3 rejects smaller parts except the last;
 *   <li>{@code partCount ≤ 100}: one batch of urls in the create response, one {@code ListParts}
 *       page;
 *   <li>whole MiB part sizes, and only the LAST part may be shorter.
 * </ul>
 */
public record PartPlan(long size, long partSize, int partCount) {

  public static final long MIB = 1024L * 1024;
  public static final long MIN_PART_SIZE = 5 * MIB;
  public static final int MAX_PARTS = 100;
  public static final long MAX_SIZE = 10 * 1024 * MIB; // 10 GiB

  public PartPlan {
    if (partCount < 1 || partCount > MAX_PARTS) {
      throw new IllegalArgumentException("partCount must be 1.." + MAX_PARTS + ": " + partCount);
    }
    if (partSize < MIN_PART_SIZE || partSize % MIB != 0) {
      throw new IllegalArgumentException("partSize must be whole MiB ≥ 5 MiB: " + partSize);
    }
    // ⭐ the last part holds the remainder: 1 .. partSize bytes
    if (size <= partSize * (partCount - 1) || size > partSize * partCount) {
      throw new IllegalArgumentException(
          "size " + size + " doesn't fit " + partCount + " × " + partSize);
    }
  }

  /** The plan for a file the client declared; 422 {@code UPLOAD_TOO_LARGE} above 10 GiB. */
  public static PartPlan forSize(long size) {
    if (size < 1) {
      throw new IllegalArgumentException("size must be positive: " + size);
    }
    if (size > MAX_SIZE) {
      throw new RuleViolationException(
          "UPLOAD_TOO_LARGE",
          "A video can be at most 10 GiB.",
          Map.of("maxBytes", MAX_SIZE, "sizeBytes", size));
    }
    long perPart = Math.ceilDiv(size, MAX_PARTS);
    long partSize = Math.max(MIN_PART_SIZE, Math.ceilDiv(perPart, MIB) * MIB);
    return new PartPlan(size, partSize, Math.toIntExact(Math.ceilDiv(size, partSize)));
  }

  /** Bytes part {@code number} (1-based) must have. */
  public long expectedSize(int number) {
    if (number < 1 || number > partCount) {
      throw new IllegalArgumentException("no part " + number + " in a " + partCount + "-part plan");
    }
    return number < partCount ? partSize : size - partSize * (partCount - 1);
  }

  /**
   * ⭐ The provider's parts vs this plan: which are missing, which have the wrong size, which
   * shouldn't exist. Complete only when all three are empty.
   */
  public PartCheck check(List<StoredPart> stored) {
    TreeSet<Integer> missing = new TreeSet<>();
    for (int n = 1; n <= partCount; n++) {
      missing.add(n);
    }
    List<Integer> wrongSize = new ArrayList<>();
    List<Integer> unexpected = new ArrayList<>();
    for (StoredPart part : stored) {
      if (part.number() < 1 || part.number() > partCount) {
        unexpected.add(part.number());
        continue;
      }
      missing.remove(part.number());
      if (part.size() != expectedSize(part.number())) {
        wrongSize.add(part.number());
      }
    }
    return new PartCheck(List.copyOf(missing), List.copyOf(wrongSize), List.copyOf(unexpected));
  }

  /** The outcome of {@link #check}. */
  public record PartCheck(
      List<Integer> missing, List<Integer> wrongSize, List<Integer> unexpected) {

    public boolean complete() {
      return missing.isEmpty() && wrongSize.isEmpty() && unexpected.isEmpty();
    }

    /** 422 {@code UPLOAD_INCOMPLETE}, telling the uploader exactly which parts to (re)send. */
    public RuleViolationException toProblem() {
      return new RuleViolationException(
          "UPLOAD_INCOMPLETE",
          "Some parts are missing or have the wrong size. Upload them and complete again.",
          Map.of("missing", missing, "wrongSize", wrongSize, "unexpected", unexpected));
    }
  }
}

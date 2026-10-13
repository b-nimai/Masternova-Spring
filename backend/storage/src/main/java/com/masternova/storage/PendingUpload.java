package com.masternova.storage;

import java.time.Instant;

/** A multipart upload the provider still holds open — what the orphan sweeper looks for (7.6). */
public record PendingUpload(String key, String uploadId, Instant initiated) {}

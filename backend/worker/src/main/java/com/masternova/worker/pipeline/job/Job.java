package com.masternova.worker.pipeline.job;

import java.util.UUID;

/** A claimed job: what to do, for which asset, and how many times it has been tried. */
public record Job(UUID id, UUID assetId, JobType type, String rung, int attempts) {}

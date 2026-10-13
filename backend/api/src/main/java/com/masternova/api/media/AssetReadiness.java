package com.masternova.api.media;

import java.util.Optional;

/** What catalog needs to know about an asset at its publish gate. */
public record AssetReadiness(boolean ready, Optional<Integer> durationSeconds) {}

package com.masternova.api.media.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data directly in {@code domain/} (media LLD §4) — as in catalog. */
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

  List<MediaAsset> findByIdIn(Collection<UUID> ids);
}

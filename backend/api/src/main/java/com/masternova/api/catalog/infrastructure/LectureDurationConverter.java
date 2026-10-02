package com.masternova.api.catalog.infrastructure;

import com.masternova.api.catalog.domain.LectureDuration;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@code LectureDuration} ↔ an {@code integer} column of seconds.
 *
 * <p>⭐ {@code autoApply = true}: Hibernate uses it for EVERY attribute of type {@code
 * LectureDuration}, so the entity in {@code domain/} never names this class — the dependency points
 * infrastructure → domain, as the module layering requires (a {@code @Convert(converter = …)} on
 * the entity would point the other way).
 *
 * <p>Null in, null out: JPA calls converters for nulls too, and the column's NOT NULL constraint is
 * the place that refuses them.
 */
@Converter(autoApply = true)
class LectureDurationConverter implements AttributeConverter<LectureDuration, Integer> {

  @Override
  public Integer convertToDatabaseColumn(LectureDuration duration) {
    return duration == null ? null : duration.seconds();
  }

  @Override
  public LectureDuration convertToEntityAttribute(Integer seconds) {
    return seconds == null ? null : LectureDuration.ofSeconds(seconds);
  }
}

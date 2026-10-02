package com.masternova.api.platform;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an unsafe endpoint that has no version to guard it (checkout, duplicate course, complete
 * upload): calling it WITHOUT an {@code Idempotency-Key} header is a 400. Any unsafe request that
 * DOES carry the header is handled idempotently, annotated or not (API conventions §4).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface IdempotencyKeyRequired {}

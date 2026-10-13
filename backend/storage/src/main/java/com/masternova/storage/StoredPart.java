package com.masternova.storage;

/** One part of a multipart upload, as the PROVIDER reports it ({@code ListParts}, ADR-0014). */
public record StoredPart(int number, long size, String etag) {}

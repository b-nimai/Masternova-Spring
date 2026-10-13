package com.masternova.storage;

/** What {@code HEAD} says about an object. */
public record StoredObject(String key, long size, String contentType) {}

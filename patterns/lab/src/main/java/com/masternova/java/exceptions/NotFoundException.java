package com.masternova.java.exceptions;

/** A resource doesn't exist — or isn't visible to the caller (same answer on purpose: no leaks). */
public final class NotFoundException extends MasternovaException {

  private final String resource;
  private final String id;

  public NotFoundException(String resource, String id) {
    super("NOT_FOUND", resource + " " + id + " was not found");
    this.resource = resource;
    this.id = id;
  }

  public String resource() {
    return resource;
  }

  public String id() {
    return id;
  }
}

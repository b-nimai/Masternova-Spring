package com.masternova.storage;

/**
 * The provider no longer knows this multipart upload id: it was completed, aborted or swept. Only
 * the caller can tell which (media LLD §5: {@code HEAD} the object).
 */
public class NoSuchUploadException extends StorageException {

  public NoSuchUploadException(String uploadId, Throwable cause) {
    super("no such multipart upload: " + uploadId, cause);
  }
}

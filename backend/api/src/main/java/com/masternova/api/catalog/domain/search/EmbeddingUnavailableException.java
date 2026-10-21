package com.masternova.api.catalog.domain.search;

/**
 * The embedding model can't answer now (down, slow, not deployed, or the breaker is open). Not a
 * client error and not a 500: search catches it and answers from keywords ({@code degraded}).
 */
public class EmbeddingUnavailableException extends RuntimeException {
  public EmbeddingUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}

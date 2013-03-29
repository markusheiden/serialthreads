package org.serialthreads.transformer;

/**
 * Exception to abort class transformation, e.g. when class does not meet requirements.
 */
public class NotTransformableException extends RuntimeException {
  public NotTransformableException(String message) {
    super(message);
  }

  public NotTransformableException(String message, Throwable cause) {
    super(message, cause);
  }
}

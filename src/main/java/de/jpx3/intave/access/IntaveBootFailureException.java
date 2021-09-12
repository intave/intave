package de.jpx3.intave.access;

/**
 * An exception describing a fatal issue within the boot procedure of Intave.
 * Issues of this type are usually caused by a user's mistake.
 */
public class IntaveBootFailureException extends RuntimeException {
  public IntaveBootFailureException() {
    super();
  }

  public IntaveBootFailureException(String message) {
    super(message);
  }

  public IntaveBootFailureException(String message, Throwable cause) {
    super(message, cause);
  }

  public IntaveBootFailureException(Throwable cause) {
    super(cause);
  }

  protected IntaveBootFailureException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}

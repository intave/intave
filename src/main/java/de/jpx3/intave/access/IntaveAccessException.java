package de.jpx3.intave.access;

/**
 * An IntaveAccessException states that an error is caused in access of Intave by a non-Intave system
 */
public class IntaveAccessException extends IntaveInternalException {
  public IntaveAccessException() {
    super();
  }

  public IntaveAccessException(String message) {
    super(message);
  }

  public IntaveAccessException(String message, Throwable cause) {
    super(message, cause);
  }

  public IntaveAccessException(Throwable cause) {
    super(cause);
  }
}

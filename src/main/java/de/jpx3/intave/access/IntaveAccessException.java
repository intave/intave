package de.jpx3.intave.access;

public class IntaveAccessException extends IntaveException {
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

package de.jpx3.intave.access.check;

import de.jpx3.intave.access.IntaveAccessException;

public final class UnknownCheckException extends IntaveAccessException {
  public UnknownCheckException() {
    super();
  }

  public UnknownCheckException(String message) {
    super(message);
  }

  public UnknownCheckException(String message, Throwable cause) {
    super(message, cause);
  }

  public UnknownCheckException(Throwable cause) {
    super(cause);
  }
}

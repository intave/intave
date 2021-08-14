package de.jpx3.intave.access;

/**
 * An IntaveColdException fires when a non-Intave entity
 * tries to access a resource of Intave or Intave itself,
 * whilst Intave is not fully enabled yet.
 * See {@link IntaveAccessException} for access-released exceptions.
 */
public final class IntaveColdException extends IntaveAccessException {
  public IntaveColdException() {
    super();
  }

  public IntaveColdException(String message) {
    super(message);
  }

  public IntaveColdException(String message, Throwable cause) {
    super(message, cause);
  }

  public IntaveColdException(Throwable cause) {
    super(cause);
  }
}

package de.jpx3.intave.access;

/**
 * An exception describing an issue with Intaves dependencies.
 * This usually occurs when a resouce is outdated
 */
public final class InvalidDependencyException extends IntaveBootFailureException {
  public InvalidDependencyException() {
    super();
  }

  public InvalidDependencyException(String message) {
    super(message);
  }

  public InvalidDependencyException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidDependencyException(Throwable cause) {
    super(cause);
  }
}

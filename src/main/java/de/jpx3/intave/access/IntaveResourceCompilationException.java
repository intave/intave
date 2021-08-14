package de.jpx3.intave.access;

/**
 * An exception describing issues related to resource-complication faults.
 */
public final class IntaveResourceCompilationException extends IntaveBootFailureException {
  public IntaveResourceCompilationException() {
  }

  public IntaveResourceCompilationException(String message) {
    super(message);
  }

  public IntaveResourceCompilationException(String message, Throwable cause) {
    super(message, cause);
  }

  public IntaveResourceCompilationException(Throwable cause) {
    super(cause);
  }
}

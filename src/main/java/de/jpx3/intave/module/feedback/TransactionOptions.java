package de.jpx3.intave.module.feedback;

public final class TransactionOptions {
  public static int SELF_SYNCHRONIZATION = 1;
  public static int APPEND_ON_OVERFLOW = 2;
  @Deprecated
  public static int APPEND = 4;

  public static boolean matches(int option, int options) {
    return (options & option) != 0;
  }
}

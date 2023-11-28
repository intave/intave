package de.jpx3.intave.module.feedback;

public final class FeedbackOptions {
  public static int SELF_SYNCHRONIZATION = 1;
  public static int APPEND_ON_OVERFLOW = 2;
  @Deprecated
  public static int APPEND = 4;

  public static int TRACER_ENTITY_NEAR_COMBAT = 1 << 16;
  public static int TRACER_ENTITY_NEAR = 1 << 17;
  public static int TRACER_ENTITY_FAR = 1 << 18;

  public static boolean matches(int option, int options) {
    return (options & option) != 0;
  }
}

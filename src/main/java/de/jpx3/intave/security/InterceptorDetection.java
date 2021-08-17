package de.jpx3.intave.security;

import de.jpx3.intave.annotate.Relocate;

import java.io.PrintStream;

@Relocate
public final class InterceptorDetection {
  private static PrintStream original;

  public static void setup() {
    original = System.out;
    System.setOut(new InterceptorFilterPrintStream(original));
  }

  public static void revert() {
    if (System.out instanceof InterceptorFilterPrintStream) {
      System.setOut(original);
      original = null;
    } else {
      System.out.println("[Intave] Invalid print-stream usage: Please contact support");
    }
  }
}

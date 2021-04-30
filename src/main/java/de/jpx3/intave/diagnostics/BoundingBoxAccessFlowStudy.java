package de.jpx3.intave.diagnostics;

public final class BoundingBoxAccessFlowStudy {
  public static int requests;
  public static int lookups;
  public static int dynamic;

  public static void increaseRequests() {
    requests++;
  }

  public static void increaseLookups() {
    lookups++;
  }

  public static void increaseDynamic() {
    dynamic++;
  }

  public static String output() {
    return requests + " requests required " + lookups + " lookups, " + dynamic + " dynamically";
  }
}

package de.jpx3.intave.detect.checks.combat.heuristics;

import java.util.Arrays;

public enum Confidence {
  CERTAIN("!!", 10000),
  VERY_LIKELY("!", 80),
  LIKELY("?!", 40),
  PROBABLE("?", 20),
  MAYBE("??", 10),
  NONE("-", 0),

  ;

  final String output;
  final int level;

  Confidence(String output, int level) {
    this.output = output;
    this.level = level;
  }

  public int level() {
    return level;
  }

  public String output() {
    return output;
  }

  public boolean atLeast(Confidence confidence) {
    return level() >= confidence.level();
  }

  public static int levelFrom(Confidence... confidences) {
    return Arrays.stream(confidences).mapToInt(Confidence::level).sum();
  }

  public static Confidence confidenceFrom(int level) {
    Confidence highest = Confidence.NONE;
    for (Confidence value : Confidence.values()) {
      if (value.level > highest.level && value.level <= level) {
        highest = value;
      }
    }
    return highest;
  }
}

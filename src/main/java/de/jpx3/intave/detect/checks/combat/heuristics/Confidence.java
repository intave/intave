package de.jpx3.intave.detect.checks.combat.heuristics;

import java.util.Arrays;

public enum Confidence {
  CERTAIN("!!", 1600),
  VERY_LIKELY("!", 800),
  LIKELY("?!", 400),
  PROBABLE("?", 200),
  MAYBE("??", 50),
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

  public static int levelFrom(Confidence... confidences) {
    return Arrays.stream(confidences).mapToInt(Confidence::level).sum();
  }

  public static Confidence confidenceFrom(int level) {
    Confidence highest = Confidence.NONE;
    for (Confidence value : Confidence.values()) {
      if (value.level > highest.level && value.level >= level) {
        highest = value;
      }
    }
    return highest;
  }
}

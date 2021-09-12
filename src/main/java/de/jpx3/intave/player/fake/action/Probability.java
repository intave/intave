package de.jpx3.intave.player.fake.action;

import java.util.concurrent.ThreadLocalRandom;

public enum Probability {
  HIGH(5, 50),
  MEDIUM(40, 80),
  LOW(400, 700);

  private final int minProbability;
  private final int maxProbability;

  Probability(int minProbability, int maxProbability) {
    this.minProbability = minProbability;
    this.maxProbability = maxProbability;
  }

  public int minProbability() {
    return minProbability;
  }

  public int maxProbability() {
    return maxProbability;
  }

  public int randomProbability() {
    return ThreadLocalRandom.current().nextInt(this.minProbability, this.maxProbability);
  }
}

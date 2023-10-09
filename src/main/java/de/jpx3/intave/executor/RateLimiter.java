package de.jpx3.intave.executor;

import de.jpx3.intave.math.MathHelper;

import java.util.concurrent.TimeUnit;

public final class RateLimiter {
  private final int maxRequests;
  private int counter;
  private long lastReset;
  private final int cooldownPerSecond;

  public RateLimiter(int maxRequests, int cooldownPerSecond, TimeUnit cooldownUnit) {
    this.maxRequests = maxRequests;
    this.cooldownPerSecond = (int) cooldownUnit.toSeconds(cooldownPerSecond);
  }

  public void checkCooldown() {
    if (System.currentTimeMillis() - lastReset > 1000L) {
      lastReset = System.currentTimeMillis();
      if (counter > 0) {
        counter -= MathHelper.minmax(0, (int) ((System.currentTimeMillis() - lastReset) / 1000L) * cooldownPerSecond, cooldownPerSecond * 5);
        if (counter < 0) {
          counter = 0;
        }
      }
    }
  }

  public boolean acquire() {
    checkCooldown();
    if (counter < maxRequests) {
      counter++;
      return true;
    }
    return false;
  }

  public boolean checkCooldownAndAcquire() {
    checkCooldown();
    return acquire();
  }

  public int maxRequests() {
    return maxRequests;
  }

  public int counter() {
    return counter;
  }

  public long lastReset() {
    return lastReset;
  }

  public int cooldownPerSecond() {
    return cooldownPerSecond;
  }
}

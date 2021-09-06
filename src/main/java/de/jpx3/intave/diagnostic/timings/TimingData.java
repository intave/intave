package de.jpx3.intave.diagnostic.timings;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2019
 */

public class TimingData implements Cloneable {

  private volatile long totalDuration;
  private volatile long calls;

  private volatile boolean immutable = false;

  public void addTime(long durationToAdd) {
    setTotalDuration(getTotalDuration() + durationToAdd);
  }

  public void increaseCallCount() {
    setCalls(getCalls() + 1);
  }

  public long getTotalDuration() {
    return totalDuration;
  }

  private synchronized void setTotalDuration(long totalDuration) {
    if (immutable) {
      throw new UnsupportedOperationException();
    }

    this.totalDuration = totalDuration;
  }

  public long getCalls() {
    return calls;
  }

  private synchronized void setCalls(long calls) {
    if (immutable) {
      throw new UnsupportedOperationException();
    }

    this.calls = calls;
  }

  public void makeImmutable() {
    immutable = true;
  }

  @Override
  public TimingData clone() {
    try {
      return (TimingData) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new Error(e);
    }
  }
}

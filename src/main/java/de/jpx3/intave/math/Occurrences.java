package de.jpx3.intave.math;

import de.jpx3.intave.annotate.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Deprecated
public final class Occurrences<T extends Number> implements Iterable<T> {
  private long count;
  private long purgeDelay = 1000 * 60 * 3;
  private final Set<T> values = new HashSet<>();
  private final List<T> scoreboardOccurrenceSorted = new ArrayList<>();
  private final List<T> scoreboardValueSorted = new ArrayList<>();
  private final Map<T, AtomicLong> counter = new HashMap<>();
  private final Lock lock = new ReentrantLock();

  private long lastPurge = System.currentTimeMillis();

  public Occurrences() {
    this.count = 0;
  }

  public Occurrences(long purgeDelay) {
    this.purgeDelay = purgeDelay;
  }

  public void occurred(T input) {
    if (input == null) {
      return;
    }
    try {
      lock.lock();
      checkPurge();
      counter.computeIfAbsent(input, t -> new AtomicLong(0)).incrementAndGet();
      count++;

      if (!values.contains(input)) {
        scoreboardValueSorted.add(input);
        scoreboardValueSorted.sort(Comparator.comparingDouble(Number::doubleValue));
        scoreboardOccurrenceSorted.add(input);
        values.add(input);
      }

      // push up until the element is at the right posi3tion
      int index = scoreboardOccurrenceSorted.indexOf(input);
      while (index > 0) {
        T above = scoreboardOccurrenceSorted.get(index - 1);
        if (counter.get(input).get() > counter.get(above).get()) {
          scoreboardOccurrenceSorted.set(index - 1, input);
          scoreboardOccurrenceSorted.set(index, above);
          index--;
        } else {
          break;
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private void checkPurge() {
    // check if purge is needed
    if (System.currentTimeMillis() - lastPurge > purgeDelay) {
      // purge by multiplying with 0.6
      for (T t : scoreboardOccurrenceSorted) {
        counter.get(t).set((long) (counter.get(t).get() * 0.6));
      }
      count = (int) (count * 0.6d);
      lastPurge = System.currentTimeMillis();
    }
  }

  @Nullable
  public T median() {
    try {
      lock.lock();
      if (count == 0) {
        return null;
      }
      return scoreboardOccurrenceSorted.get(scoreboardOccurrenceSorted.size() / 2);
    } finally {
      lock.unlock();
    }
  }

  public double mean() {
    try {
      lock.lock();
      if (count == 0) {
        return 0;
      }
      double sum = 0;
      for (T t : scoreboardOccurrenceSorted) {
        sum += t.doubleValue() * counter.get(t).doubleValue();
      }
      return sum / (double)count;
    } finally {
      lock.unlock();
    }
  }

  public double standardDeviation() {
    try {
      lock.lock();
      if (count == 0) {
        return 0;
      }
      return Math.sqrt(variance());
    } finally {
      lock.unlock();
    }
  }

  public double variance() {
    try {
      lock.lock();
      if (count == 0) {
        return 0;
      }
      double mean = mean();
      double sum = 0;
      for (T t : scoreboardOccurrenceSorted) {
        sum += Math.pow(counter.get(t).doubleValue() - mean, 2);
      }
      return sum / count;
    } finally {
      lock.unlock();
    }
  }

  public List<String> plotAsBarDiagram(int height) {
    try {
      lock.lock();
      if (scoreboardOccurrenceSorted.isEmpty()) {
        return Collections.singletonList("Not enough data to plot a diagram.");
      }
      int max = counter.get(scoreboardOccurrenceSorted.get(0)).intValue();
      // maximum 10 lines
      List<String> result = new ArrayList<>();
      int step = Math.max(1, max / height);
      for (int i = max; i >= 0; i -= step) {
        StringBuilder builder = new StringBuilder();
        for (T t : scoreboardValueSorted) {
          builder.append(counter.get(t).intValue() >= i ? "O" : " ");
        }
        result.add(builder.toString());
      }
      StringBuilder builder = new StringBuilder();
      for (T t : scoreboardValueSorted) {
        builder.append(t);
      }
      result.add(builder.toString());
      return result;
    } finally {
      lock.unlock();
    }
  }

  public void reset(T input) {
    try {
      lock.lock();
      if (!counter.containsKey(input)) {
        return;
      }
      counter.remove(input);
      scoreboardOccurrenceSorted.remove(input);
      count--;
    } finally {
      lock.unlock();
    }
  }

  public void resetAll() {
    try {
      lock.lock();
      counter.clear();
      scoreboardOccurrenceSorted.clear();
      count = 0;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Iterator<T> iterator() {
    return scoreboardOccurrenceSorted.iterator();
  }

  @Override
  public Spliterator<T> spliterator() {
    return scoreboardOccurrenceSorted.spliterator();
  }

  @Override
  public void forEach(Consumer<? super T> action) {
    scoreboardOccurrenceSorted.forEach(action);
  }
}
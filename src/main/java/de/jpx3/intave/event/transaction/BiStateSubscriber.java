package de.jpx3.intave.event.transaction;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public interface BiStateSubscriber<K extends BiState<T>, T> {
  void flush();

  void append();
}

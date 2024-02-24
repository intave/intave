package de.jpx3.intave.module.feedback;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class FeedbackQueue {
  private static final int MAX_DIRECT_SIZE = 256;
  private final FeedbackEntry[] directLocalAccess = new FeedbackEntry[MAX_DIRECT_SIZE];
  private final Map<Short, FeedbackEntry> fallbackLocalAccess = new HashMap<>();
  private FeedbackEntry head, tail;
  private int size;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Lock writeLock = lock.writeLock();

  public synchronized void add(FeedbackRequest<?> request) {
    FeedbackEntry entry = new FeedbackEntry(request);
    writeLock.lock();
    try {
      short userKey = request.userKey();
      if (userKey >= 0 && userKey < MAX_DIRECT_SIZE) {
        directLocalAccess[userKey] = entry;
      } else {
        fallbackLocalAccess.put(userKey, entry);
      }
      if (head == null) {
        head = tail = entry;
      } else {
        tail.setNext(entry);
        tail = entry;
      }
      size++;
    } finally {
      writeLock.unlock();
    }
  }

  public synchronized FeedbackRequest<?> peek() {
    readLock.lock();
    try {
      return head == null ? null : head.request;
    } finally {
      readLock.unlock();
    }
  }

  public synchronized FeedbackRequest<?> peek(short userKey) {
    readLock.lock();
    try {
      FeedbackEntry entry;
      if (userKey >= 0 && userKey < MAX_DIRECT_SIZE) {
        entry = directLocalAccess[userKey];
      } else {
        entry = fallbackLocalAccess.get(userKey);
      }
      return entry == null ? null : entry.request;
    } finally {
      readLock.unlock();
    }
  }

  public synchronized FeedbackRequest<?> poll() {
    writeLock.lock();
    try {
      if (head == null) {
        return null;
      }
      FeedbackEntry entry = head;
      head = head.next();
      if (head == null) {
        tail = null;
      }
      short userKey = entry.request.userKey();
      if (userKey >= 0 && userKey < MAX_DIRECT_SIZE) {
        directLocalAccess[userKey] = null;
      } else {
        fallbackLocalAccess.remove(userKey);
      }
      size--;
      return entry.request;
    } finally {
      writeLock.unlock();
    }
  }

  // can be a bit expensive, shouldn't be used too often though
  public synchronized List<FeedbackRequest<?>> pollUpTo(long globalKey) {
    writeLock.lock();
    try {
      if (head == null) {
        return Collections.emptyList();
      }
      FeedbackEntry entry = head;
      List<FeedbackRequest<?>> list = null;
      while (entry != null && entry.globalIndex() < globalKey) {
        if (list == null) {
          list = new ArrayList<>();
        }
        list.add(entry.request);
        head = head.next();
        if (head == null) {
          tail = null;
        }
        short userKey = entry.request.userKey();
        if (userKey >= 0 && userKey < MAX_DIRECT_SIZE) {
          directLocalAccess[userKey] = null;
        } else {
          fallbackLocalAccess.remove(userKey);
        }
        size--;
        entry = head;
      }
      return list == null ? Collections.emptyList() : list;
    } finally {
      writeLock.unlock();
    }
  }

  public synchronized boolean hasUserKey(short userKey) {
    readLock.lock();
    try {
      if (userKey >= 0 && userKey < MAX_DIRECT_SIZE) {
        return directLocalAccess[userKey] != null;
      } else {
        return fallbackLocalAccess.containsKey(userKey);
      }
    } finally {
      readLock.unlock();
    }
  }

  public synchronized int size() {
    readLock.lock();
    try {
      return size;
    } finally {
      readLock.unlock();
    }
  }

  public static class FeedbackEntry {
    private final FeedbackRequest<?> request;
    private FeedbackEntry next;

    public FeedbackEntry(FeedbackRequest<?> request) {
      this.request = request;
    }

    public FeedbackEntry next() {
      return next;
    }

    public void setNext(FeedbackEntry next) {
      this.next = next;
    }

    public long globalIndex() {
      return request.num();
    }
  }
}

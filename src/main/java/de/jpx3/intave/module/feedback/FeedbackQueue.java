package de.jpx3.intave.module.feedback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class FeedbackQueue {
  private static final int MAX_DIRECT_SIZE = 2048;
  private static final int DIRECT_TRANSLATION = 1024;
  private final FeedbackEntry[] directLocalAccess = new FeedbackEntry[MAX_DIRECT_SIZE];
  private final Map<Short, FeedbackEntry> fallbackLocalAccess = new ConcurrentHashMap<>();
  private FeedbackEntry head, tail;
  private int size;
  // Under Folia this per-user queue is touched concurrently (region packet
  // thread on join, inbound-ack drain, per-tick transaction task). The former
  // ReadWriteLock let the region thread block for seconds behind writers and
  // deadlocked the tick. All operations here are O(1) (pollUpTo yields
  // periodically), and the per-user feedback lock is always taken *before* this
  // lock and never after, so a single mutex cannot form a lock cycle.
  private final Lock lock = new ReentrantLock();

  public void add(FeedbackRequest<?> request) {
    FeedbackEntry entry = new FeedbackEntry(request);
    lock.lock();
    try {
      short userKey = request.userKey();
      if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
        short localAccessKey = (short) (userKey + DIRECT_TRANSLATION);
        if (directLocalAccess[localAccessKey] != null) {
          directLocalAccess[localAccessKey].appendSameUserKey(entry);
        } else {
          directLocalAccess[localAccessKey] = entry;
        }
      } else if (fallbackLocalAccess.containsKey(userKey)) {
        fallbackLocalAccess.get(userKey).appendSameUserKey(entry);
      } else {
        fallbackLocalAccess.put(userKey, entry);
      }
      if (head == null) {
        head = tail = entry;
      } else {
        tail.setFollowingRequest(entry);
        tail = entry;
      }
      size++;
    } finally {
      lock.unlock();
    }
  }

  public FeedbackRequest<?> peek() {
    lock.lock();
    try {
      return head == null ? null : head.request;
    } finally {
      lock.unlock();
    }
  }

  public FeedbackRequest<?> peek(short userKey) {
    lock.lock();
    try {
      FeedbackEntry entry;
      if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
        entry = directLocalAccess[userKey + DIRECT_TRANSLATION];
      } else {
        entry = fallbackLocalAccess.get(userKey);
      }
      return entry == null ? null : entry.request;
    } finally {
      lock.unlock();
    }
  }

  public FeedbackRequest<?> poll() {
    lock.lock();
    try {
      if (head == null) {
        return null;
      }
      FeedbackEntry entry = head;
      head = head.followingRequest();
      if (head == null) {
        tail = null;
      }
      short userKey = entry.request.userKey();
      FeedbackEntry replacement = entry.nextSameUserKeyEntry();
      if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
        directLocalAccess[userKey + DIRECT_TRANSLATION] = replacement;
      } else if (replacement != null) {
        fallbackLocalAccess.put(userKey, replacement);
      } else {
        fallbackLocalAccess.remove(userKey);
      }
      if (replacement != null) {
        replacement.sameUserKeyTail = entry.sameUserKeyTail;
        entry.sameUserKeyNext = null;
      }
      size--;
      return entry.request;
    } finally {
      lock.unlock();
    }
  }

  // can be a bit expensive, shouldn't be used too often though
  public List<FeedbackRequest<?>> pollUpTo(long globalKey) {
    lock.lock();
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
        head = head.followingRequest();
        if (head == null) {
          tail = null;
        }
        FeedbackEntry replacement = entry.nextSameUserKeyEntry();
        short userKey = entry.request.userKey();
        if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
          directLocalAccess[userKey + DIRECT_TRANSLATION] = replacement;
        } else if (replacement != null) {
          fallbackLocalAccess.put(userKey, replacement);
        } else {
          fallbackLocalAccess.remove(userKey);
        }
        if (replacement != null) {
          replacement.sameUserKeyTail = entry.sameUserKeyTail;
          entry.sameUserKeyNext = null;
        }
        size--;
        entry = head;
        if (list.size() > 500 && list.size() % 100 == 0) {
          lock.unlock();
          lock.lock();
        }
      }
      return list == null ? Collections.emptyList() : list;
    } finally {
      lock.unlock();
    }
  }

  public boolean hasUserKey(short userKey) {
    lock.lock();
    try {
      if (userKey > -DIRECT_TRANSLATION && userKey < DIRECT_TRANSLATION) {
        return directLocalAccess[userKey + DIRECT_TRANSLATION] != null;
      } else {
        return fallbackLocalAccess.containsKey(userKey);
      }
    } finally {
      lock.unlock();
    }
  }

  public int size() {
    lock.lock();
    try {
      return size;
    } finally {
      lock.unlock();
    }
  }

  public static class FeedbackEntry {
    private final FeedbackRequest<?> request;
    private FeedbackEntry nextRequest;
    private FeedbackEntry sameUserKeyNext;
    private FeedbackEntry sameUserKeyTail;

    public FeedbackEntry(FeedbackRequest<?> request) {
      this.request = request;
    }

    public FeedbackEntry followingRequest() {
      return nextRequest;
    }

    public void setFollowingRequest(FeedbackEntry nextRequest) {
      this.nextRequest = nextRequest;
    }

    public FeedbackEntry nextSameUserKeyEntry() {
      return sameUserKeyNext;
    }

    public void appendSameUserKey(FeedbackEntry entry) {
      if (sameUserKeyNext == null) {
        sameUserKeyNext = entry;
      } else {
        sameUserKeyTail.sameUserKeyNext = entry;
      }
      sameUserKeyTail = entry;
    }

    public long globalIndex() {
      return request.num();
    }

    @Override
    public String toString() {
      return "FeedbackEntry{" +
        "request=" + request +
        ", nextRequest=" + nextRequest +
        ", sameUserKeyNext=" + sameUserKeyNext +
        ", userKey=" + request.userKey() +
        '}';
    }
  }
}

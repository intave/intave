package de.jpx3.intave.user.storage;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class NerferStorage implements Storage {
  private final Map<String, Long> nerfers = new ConcurrentHashMap<>();
  private final Lock lock = new ReentrantLock();
  private long savedAt;

  @Override
  public void writeTo(ByteArrayDataOutput output) {
    lock.lock();
    try {
      output.writeLong(savedAt = System.currentTimeMillis());
      output.writeInt(nerfers.size());
      for (Map.Entry<String, Long> entry : nerfers.entrySet()) {
        output.writeUTF(entry.getKey());
        output.writeLong(entry.getValue());
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void readFrom(ByteArrayDataInput input) {
    lock.lock();
    try {
      savedAt = input.readLong();
      int size = input.readInt();
      for (int i = 0; i < size; i++) {
        String key = input.readUTF();
        long value = input.readLong();
        nerfers.put(key, value);
      }
    } finally {
      lock.unlock();
    }
  }

  public void addNerfer(String nerfer, long expiration) {
    lock.lock();
    try {
      nerfers.put(nerfer, expiration);
    } finally {
      lock.unlock();
    }
  }

  public Map<String, Long> nerfers() {
    lock.lock();
    try {
      return nerfers;
    } finally {
      lock.unlock();
    }
  }

  public void clearNerfers() {
    lock.lock();
    try {
      nerfers.clear();
    } finally {
      lock.unlock();
    }
  }

  public long savedAt() {
    return savedAt;
  }

  @Override
  public int id() {
    return 3;
  }

  @Override
  public int version() {
    return 2;
  }
}

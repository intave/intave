/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.share.opt;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared implementation for maps optimized for overwhelmingly read-heavy workloads.
 *
 * <p>All operations are thread-safe. Reads are lock-free and allocation-free. Every mutation
 * copies and atomically publishes the backing table, so mutations are O(n) and should be rare.
 * Null keys and values are not supported.</p>
 *
 * <p>{@link #computeIfAbsent(Object, Function)} serializes cache misses and invokes the mapping
 * function while holding the mutation lock. Mapping functions should therefore be short and
 * should not recursively update this map.</p>
 */
public abstract class CopyOnWriteMap<K, V> {
  private volatile Table table = Table.EMPTY;

  public final V get(K key) {
    return valueIn(table, Objects.requireNonNull(key, "key"));
  }

  public final boolean containsKey(K key) {
    return get(key) != null;
  }

  public final int size() {
    return table.size;
  }

  public final boolean isEmpty() {
    return table.size == 0;
  }

  public final synchronized V put(K key, V value) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    Table current = table;
    V previous = valueIn(current, key);
    table = with(current, key, value);
    return previous;
  }

  public final synchronized V putIfAbsent(K key, V value) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    Table current = table;
    V existing = valueIn(current, key);
    if (existing != null) {
      return existing;
    }
    table = with(current, key, value);
    return null;
  }

  public final V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(mappingFunction, "mappingFunction");
    V existing = valueIn(table, key);
    if (existing != null) {
      return existing;
    }
    return computeIfAbsentSynchronized(key, mappingFunction);
  }

  private synchronized V computeIfAbsentSynchronized(
    K key, Function<? super K, ? extends V> mappingFunction
  ) {
    V existing = valueIn(table, key);
    if (existing != null) {
      return existing;
    }
    V computed = Objects.requireNonNull(mappingFunction.apply(key), "mappingFunction returned null");
    Table current = table;
    existing = valueIn(current, key);
    if (existing != null) {
      return existing;
    }
    table = with(current, key, computed);
    return computed;
  }

  public final synchronized V remove(K key) {
    Objects.requireNonNull(key, "key");
    Table current = table;
    int removedIndex = findIndex(current, key);
    if (removedIndex < 0) {
      return null;
    }
    V removed = valueAt(current, removedIndex);
    table = without(current, removedIndex);
    return removed;
  }

  public final synchronized void clear() {
    table = Table.EMPTY;
  }

  /**
   * Visits the entries present when traversal starts. Concurrent mutations do not alter the
   * snapshot being traversed and do not block it.
   */
  public final void forEach(BiConsumer<? super K, ? super V> action) {
    Objects.requireNonNull(action, "action");
    Table snapshot = table;
    for (int index = 0; index < snapshot.keys.length; index++) {
      if (snapshot.keys[index] != null) {
        action.accept(keyAt(snapshot, index), valueAt(snapshot, index));
      }
    }
  }

  /**
   * Visits the keys present when traversal starts.
   */
  public final void forEachKey(Consumer<? super K> action) {
    Objects.requireNonNull(action, "action");
    Table snapshot = table;
    for (int index = 0; index < snapshot.keys.length; index++) {
      if (snapshot.keys[index] != null) {
        action.accept(keyAt(snapshot, index));
      }
    }
  }

  /**
   * Visits the values present when traversal starts.
   */
  public final void forEachValue(Consumer<? super V> action) {
    Objects.requireNonNull(action, "action");
    Table snapshot = table;
    for (int index = 0; index < snapshot.keys.length; index++) {
      if (snapshot.keys[index] != null) {
        action.accept(valueAt(snapshot, index));
      }
    }
  }

  protected abstract int hashOf(Object key);

  protected abstract boolean keysEqual(Object queried, Object stored);

  @SuppressWarnings("unchecked")
  private V valueIn(Table source, K key) {
    int index = findIndex(source, key);
    return index < 0 ? null : (V) source.values[index];
  }

  private int findIndex(Table source, K key) {
    if (source.keys.length == 0) {
      return -1;
    }
    int mask = source.keys.length - 1;
    int index = index(key, mask);
    Object cachedKey;
    while ((cachedKey = source.keys[index]) != null) {
      if (keysEqual(key, cachedKey)) {
        return index;
      }
      index = (index + 1) & mask;
    }
    return -1;
  }

  private Table with(Table source, K key, V value) {
    int existingIndex = findIndex(source, key);
    if (existingIndex >= 0) {
      Object[] updatedValues = source.values.clone();
      updatedValues[existingIndex] = value;
      return new Table(source.keys, updatedValues, source.size);
    }

    int capacity = source.keys.length == 0 ? Table.MINIMUM_CAPACITY : source.keys.length;
    if ((source.size + 1) * 2 > capacity) {
      capacity <<= 1;
    }
    Object[] updatedKeys = new Object[capacity];
    Object[] updatedValues = new Object[capacity];
    copyInto(source, updatedKeys, updatedValues, -1);
    insert(updatedKeys, updatedValues, key, value);
    return new Table(updatedKeys, updatedValues, source.size + 1);
  }

  private Table without(Table source, int removedIndex) {
    int updatedSize = source.size - 1;
    if (updatedSize == 0) {
      return Table.EMPTY;
    }
    int capacity = source.keys.length;
    if (capacity > Table.MINIMUM_CAPACITY && updatedSize * 4 <= capacity) {
      capacity >>= 1;
    }
    Object[] updatedKeys = new Object[capacity];
    Object[] updatedValues = new Object[capacity];
    copyInto(source, updatedKeys, updatedValues, removedIndex);
    return new Table(updatedKeys, updatedValues, updatedSize);
  }

  private void copyInto(
    Table source, Object[] targetKeys, Object[] targetValues, int excludedIndex
  ) {
    for (int index = 0; index < source.keys.length; index++) {
      Object key = source.keys[index];
      if (key != null && index != excludedIndex) {
        insert(targetKeys, targetValues, key, source.values[index]);
      }
    }
  }

  private void insert(Object[] keys, Object[] values, Object key, Object value) {
    int mask = keys.length - 1;
    int index = index(key, mask);
    while (keys[index] != null) {
      index = (index + 1) & mask;
    }
    keys[index] = key;
    values[index] = value;
  }

  private int index(Object key, int mask) {
    int hash = hashOf(key);
    hash ^= hash >>> 16;
    return hash & mask;
  }

  @SuppressWarnings("unchecked")
  private K keyAt(Table source, int index) {
    return (K) source.keys[index];
  }

  @SuppressWarnings("unchecked")
  private V valueAt(Table source, int index) {
    return (V) source.values[index];
  }

  private static final class Table {
    private static final int MINIMUM_CAPACITY = 4;
    private static final Table EMPTY = new Table(new Object[0], new Object[0], 0);

    private final Object[] keys;
    private final Object[] values;
    private final int size;

    private Table(Object[] keys, Object[] values, int size) {
      this.keys = keys;
      this.values = values;
      this.size = size;
    }
  }
}

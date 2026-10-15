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

/**
 * A read-optimized copy-on-write map using standard {@link Object#equals(Object)} and
 * {@link Object#hashCode()} key semantics.
 */
public final class CopyOnWriteHashMap<K, V> extends CopyOnWriteMap<K, V> {
  @Override
  protected int hashOf(Object key) {
    return key.hashCode();
  }

  @Override
  protected boolean keysEqual(Object queried, Object stored) {
    return queried.equals(stored);
  }
}

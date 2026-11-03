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

package de.jpx3.intave.packet.plib.reader;

import org.jetbrains.annotations.NotNull;

public final class MountEntityReader extends EntityReader implements EntityIterable {
  public int entityId() {
    return packet().getIntegers().read(0);
  }

  public int[] mounts() {
    return packet().getIntegerArrays().read(0);
  }

  @Override
  public @NotNull SubstitutionIterator<Integer> iterator() {
    return new SubstitutionIterator<Integer>() {
      private int slot = 0;

      @Override
      public boolean hasNext() {
        return slot < 1 + mounts().length;
      }

      @Override
      public Integer next() {
        if (slot == 0) {
          slot++;
          return packet().getIntegers().read(0);
        } else {
          return mounts()[slot++ - 1];
        }
      }

      @Override
      public void set(Integer integer) {
        if (slot == 1) {
          packet().getIntegers().write(0, integer);
        } else {
          mounts()[slot] = integer;
        }
      }
    };
  }
}

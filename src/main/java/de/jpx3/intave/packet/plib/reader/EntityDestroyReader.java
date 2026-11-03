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

import de.jpx3.intave.adapter.MinecraftVersions;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

public final class EntityDestroyReader extends AbstractPacketReader implements EntityIterable {
  private final boolean INT_LIST_ENTITY_DESTROY = MinecraftVersions.VER1_17_1.atOrAbove();
  private final boolean SINGLE_INT_ENTITY_DESTROY = !INT_LIST_ENTITY_DESTROY && MinecraftVersions.VER1_17_0.atOrAbove();
  private final boolean INT_ARRAY_ENTITY_DESTROY = !SINGLE_INT_ENTITY_DESTROY;

  @Override
  public void forEach(Consumer<? super Integer> action) {
    if (INT_LIST_ENTITY_DESTROY) {
      List<Integer> entityIds = packet().getIntLists().read(0);
      entityIds.forEach(action);
    } else if (INT_ARRAY_ENTITY_DESTROY) {
      int[] entityIDs = packet().getIntegerArrays().read(0);
      for (int entityID : entityIDs) {
        action.accept(entityID);
      }
    } else {
      action.accept(packet().getIntegers().read(0));
    }
  }

  @NotNull
  @Override
  public SubstitutionIterator<Integer> iterator() {
    if (INT_LIST_ENTITY_DESTROY) {
      List<Integer> entityIDs = packet().getIntLists().read(0);
      return new SubstitutionIterator<Integer>() {
        int index = 0;

        @Override
        public void set(Integer integer) {
          entityIDs.set(index - 1, integer);
        }

        @Override
        public boolean hasNext() {
          return index < entityIDs.size();
        }

        @Override
        public Integer next() {
          return entityIDs.get(index++);
        }
      };
    } else if (INT_ARRAY_ENTITY_DESTROY) {
      int[] entityIDs = packet().getIntegerArrays().read(0);
      return new SubstitutionIterator<Integer>() {
        int index = 0;

        @Override
        public void set(Integer integer) {
          entityIDs[index - 1] = integer;
//          packet().getIntegerArrays().write(0, entityIDs);
        }

        @Override
        public boolean hasNext() {
          return index < entityIDs.length;
        }

        @Override
        public Integer next() {
          return entityIDs[index++];
        }
      };
    } else {
      int id = packet().getIntegers().read(0);
      return new SubstitutionIterator<Integer>() {
        @Override
        public void set(Integer integer) {
          packet().getIntegers().write(0, integer);
        }

        boolean hasNext = true;

        @Override
        public boolean hasNext() {
          return hasNext;
        }

        @Override
        public Integer next() {
          hasNext = false;
          return id;
        }
      };
    }
  }
}

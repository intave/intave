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

package de.jpx3.intave.check.movement.physics.search;

import de.jpx3.intave.check.movement.physics.branch.MovementSearchBranch;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BranchFrequencySorterTest {
  @Test
  void matchesStableReferenceOrderAcrossBufferReuseAndFrequencyChanges() {
    BranchFrequencySorter sorter = new BranchFrequencySorter();
    Random random = new Random(42);
    long[] values = {0, 1, 1, 3, 100, Long.MAX_VALUE, Long.MIN_VALUE};
    for (int size : new int[]{0, 1, 4, 64, 3, 504, 36, 504}) {
      MovementSearchBranch[] branches = new MovementSearchBranch[size];
      Long2LongOpenHashMap frequencies = new Long2LongOpenHashMap();
      frequencies.defaultReturnValue(-1);
      for (int i = 0; i < size; i++) {
        branches[i] = MovementSearchBranch.blank(null)
          .withKeypress(random.nextInt(3) - 1, random.nextInt(3) - 1)
          .withSprintingSetTo(random.nextBoolean())
          .withJumped(random.nextBoolean())
          .withHandActive(random.nextBoolean())
          .withReduceTicks(random.nextInt(4));
        if (i % 3 != 0) {
          frequencies.put(branches[i].frequencyKey(), values[random.nextInt(values.length)]);
        }
      }
      MovementSearchBranch[] expected = branches.clone();
      Arrays.sort(expected, (left, right) -> Long.compare(
        frequencies.get(right.frequencyKey()), frequencies.get(left.frequencyKey())
      ));
      sorter.sort(branches, frequencies);
      for (int i = 0; i < size; i++) {
        assertSame(expected[i], branches[i], "Stable order at index " + i + " of " + size);
      }
    }
  }

  @Test
  void readsFrequencyOncePerCandidateAndRefreshesOnEachSort() {
    class CountingFrequencies extends Long2LongOpenHashMap {
      int reads;

      @Override
      public long get(long key) {
        reads++;
        return super.get(key);
      }
    }
    CountingFrequencies frequencies = new CountingFrequencies();
    MovementSearchBranch first = MovementSearchBranch.blank(null).withKeypress(1, 0);
    MovementSearchBranch second = MovementSearchBranch.blank(null).withKeypress(0, 1);
    MovementSearchBranch[] branches = {first, second};
    BranchFrequencySorter sorter = new BranchFrequencySorter();

    sorter.sort(branches, frequencies);
    assertEquals(0, frequencies.reads);
    assertSame(first, branches[0]);
    assertSame(second, branches[1]);

    frequencies.put(second.frequencyKey(), 10);
    sorter.sort(branches, frequencies);
    assertEquals(2, frequencies.reads);
    assertSame(second, branches[0]);

    frequencies.put(first.frequencyKey(), 20);
    sorter.sort(branches, frequencies);
    assertEquals(4, frequencies.reads);
    assertSame(first, branches[0]);
  }

  @Test
  void packetConfirmedItemUseIsTriedBeforeUnconfirmedSlotSwitch() {
    MovementSearchBranch inactive = MovementSearchBranch.blank(null)
      .withHandActive(false);
    MovementSearchBranch active = MovementSearchBranch.blank(null)
      .withHandActive(true);
    MovementSearchBranch[] branches = {inactive, active};

    ThreeTickSimulationSearch.preferPacketConfirmedItemUse(branches);

    assertTrue(branches[0].moveConfig().isHandActive());
    assertFalse(branches[1].moveConfig().isHandActive());
  }
}

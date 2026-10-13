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

package de.jpx3.intave.packet.reader;

import ac.intave.samples.event.InventoryActionEvent;
import de.jpx3.intave.share.WindowClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class WindowClickReaderTest {
  @Test
  void normalizesContainerClickGestures() {
    assertAction(WindowClickType.PICKUP, 12, 0, InventoryActionEvent.Action.CLICK_PRIMARY);
    assertAction(WindowClickType.PICKUP, 12, 1, InventoryActionEvent.Action.CLICK_SECONDARY);
    assertAction(WindowClickType.PICKUP, -999, 0, InventoryActionEvent.Action.DROP_CURSOR_STACK);
    assertAction(WindowClickType.PICKUP, -999, 1, InventoryActionEvent.Action.DROP_CURSOR_ONE);
    assertAction(WindowClickType.QUICK_MOVE, 12, 0, InventoryActionEvent.Action.QUICK_MOVE);
    assertAction(WindowClickType.SWAP, 12, 2, InventoryActionEvent.Action.SWAP_HOTBAR);
    assertAction(WindowClickType.SWAP, 12, 40, InventoryActionEvent.Action.SWAP_OFFHAND);
    assertAction(WindowClickType.CLONE, 12, 2, InventoryActionEvent.Action.CLONE);
    assertAction(WindowClickType.THROW, 12, 0, InventoryActionEvent.Action.DROP_SLOT_ONE);
    assertAction(WindowClickType.THROW, 12, 1, InventoryActionEvent.Action.DROP_SLOT_STACK);
    assertAction(WindowClickType.PICKUP_ALL, 12, 0, InventoryActionEvent.Action.COLLECT_TO_CURSOR);
    assertAction(WindowClickType.QUICK_CRAFT, -999, 0, InventoryActionEvent.Action.DRAG_START);
    assertAction(WindowClickType.QUICK_CRAFT, 12, 5, InventoryActionEvent.Action.DRAG_ADD_SLOT);
    assertAction(WindowClickType.QUICK_CRAFT, -999, 10, InventoryActionEvent.Action.DRAG_END);
  }

  @Test
  void recreatesItemAndCountFromHashedStack() {
    FakeItem item = new FakeItem();

    Object rebuilt = WindowClickReader.recreateNativeItemStack(
      new FakeHashedStack(item, 7, new Object()), FakeNativeItemStack.class
    );

    FakeNativeItemStack stack = (FakeNativeItemStack) rebuilt;
    assertSame(item, stack.item);
    assertEquals(7, stack.count);
  }

  @Test
  void treatsHashedEmptyStackAsEmpty() {
    assertNull(WindowClickReader.recreateNativeItemStack(
      new FakeEmptyHashedStack(), FakeNativeItemStack.class
    ));
  }

  private static void assertAction(
    WindowClickType type, int slot, int button, InventoryActionEvent.Action expected
  ) {
    assertEquals(expected, WindowClickReader.actionOf(type, slot, button));
  }

  private static final class FakeHashedStack {
    private final FakeItem item;
    private final int count;
    private final Object components;

    private FakeHashedStack(FakeItem item, int count, Object components) {
      this.item = item;
      this.count = count;
      this.components = components;
    }
  }

  private static final class FakeEmptyHashedStack {
  }

  private static final class FakeItem {
  }

  private static final class FakeNativeItemStack {
    private final FakeItem item;
    private final int count;

    private FakeNativeItemStack(FakeItem item, int count) {
      this.item = item;
      this.count = count;
    }
  }
}

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

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.SignUpdatePacket;
import de.jpx3.intave.packet.contract.immutable.WindowBulkItemsPacket;
import de.jpx3.intave.packet.contract.immutable.WindowClickHeaderPacket;
import de.jpx3.intave.packet.contract.immutable.WindowSetSlotPacket;
import de.jpx3.intave.player.FaultKicks;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.event.Cancellable;

public final class ItemCrashTracker extends Module {
  @OnSend
  public void checkOutgoingItems(User user, Cancellable cancellable, WindowBulkItemsPacket packet) {
    if (cancellable.isCancelled()) {
      return;
    }
    for (ItemStack stack : packet.items()) {
      putOnWhitelist(user, stack);
    }
    putOnWhitelist(user, packet.carriedItem());
  }

  @OnSend
  public void checkOutgoingItem(User user, Cancellable cancellable, WindowSetSlotPacket packet) {
    if (!cancellable.isCancelled()) {
      putOnWhitelist(user, packet.item());
    }
  }

  private void putOnWhitelist(User user, ItemStack stack) {
    if (stack == null) {
      return;
    }
    String type = stack.type().path();
    if (type.contains("skull") || type.contains("head")) {
      stack.skullOwnerName().ifPresent(user.meta().inventory()::registerSkullRequest);
    }
  }

  @OnReceive
  public void checkSign(User user, Cancellable cancellable, SignUpdatePacket packet) {
    if (cancellable.isCancelled()) {
      return;
    }
    for (String line : packet.lines()) {
      if (line.length() > 500) {
        cancellable.setCancelled(true);
        user.kick("Too many characters in sign update packet");
        return;
      }
    }
  }

  @OnReceive
  public void windowClickCrashFix(User user, Cancellable cancellable, WindowClickHeaderPacket packet) {
    if (cancellable.isCancelled()) {
      return;
    }
    InventoryMetadata inventoryData = user.meta().inventory();
    if (System.currentTimeMillis() - inventoryData.lastWCCReset > 10000) {
      inventoryData.windowClickCounter = 0;
      inventoryData.lastWCCReset = System.currentTimeMillis();
    }

    if (inventoryData.windowClickCounter++ > 500 && FaultKicks.INVENTORY_FAULTS) {
      user.kick("Too many inventory interactions");
      cancellable.setCancelled(true);
    }
  }
}

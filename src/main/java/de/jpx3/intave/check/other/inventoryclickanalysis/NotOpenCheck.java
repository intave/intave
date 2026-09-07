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

package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.WindowClickReader;
import de.jpx3.intave.packet.reader.WindowClickReader.InventoryClickType;
import de.jpx3.intave.packet.view.PacketEventsWindowClickView;
import de.jpx3.intave.packet.view.ProtocolLibWindowClickView;
import de.jpx3.intave.packet.view.WindowClickView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.InventoryMetadata;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;

public final class NotOpenCheck extends CheckPart<InventoryClickAnalysis> {
  private final IntavePlugin plugin;

  public NotOpenCheck(InventoryClickAnalysis parentCheck) {
    super(parentCheck);
    plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void receiveWindowClick(
    User user, WindowClickReader reader,
    Cancellable cancellable
  ) {
    handleWindowClick(user, new ProtocolLibWindowClickView(user.player(), reader, cancellable));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      WINDOW_CLICK
    }
  )
  public void receiveWindowClick(PacketReceiveEvent event) {
    PacketEventsWindowClickView view = PacketEventsWindowClickView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleWindowClick(UserRepository.userOf(player), view);
  }

  /** Engine independent container click handling; see {@link WindowClickView}. */
  private void handleWindowClick(User user, WindowClickView view) {
    Player player = user.player();
    InventoryMetadata inventory = user.meta().inventory();
    InventoryClickType clickType = view.clickType();

//    player.sendMessage(clickType.name() + "/"+view.clickedItemTypeIfPossible(player)+" " + inventory.inventoryOpen() + " " + view.containerId() + " " + view.slot() + " " + view.button());

    boolean isNativeInventoryClick = view.containerId() == 0;
    boolean forceInventoryOnClickOpen = user.meta().inventory().forceInventoryOnClickOpen;

    // Do not remove! @Richy
    if (!forceInventoryOnClickOpen) {
      return;
    }

    if (!inventory.inventoryOpen()) {
      if (user.meta().protocol().supportsInventoryAchievementPacket()) {
        Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
          .forPlayer(player)
          .withMessage("clicked in closed inventory")
          .withDetails("slot " + view.slot() + " in inventory " + view.containerId())
          .withVL(5).build();
        Modules.violationProcessor().processViolation(violation);
        Synchronizer.synchronize(user, player::closeInventory);
        view.setCancelled(true);
      } else if (isNativeInventoryClick) {
        user.meta().inventory().updateInventoryOpenState(true);
      }
    }
  }
}

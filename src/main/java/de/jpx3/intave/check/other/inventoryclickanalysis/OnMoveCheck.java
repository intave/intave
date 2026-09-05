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

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class OnMoveCheck extends CheckPart<InventoryClickAnalysis> {
  private final IntavePlugin plugin;

  public OnMoveCheck(InventoryClickAnalysis parentCheck) {
    super(parentCheck);
    plugin = IntavePlugin.singletonInstance();
  }

  @BukkitEventSubscription
  public void receiveWindowClick(InventoryClickEvent event) {
    HumanEntity whoClicked = event.getWhoClicked();
    if (!(whoClicked instanceof Player)) {
      return;
    }
    Player player = ((Player) whoClicked).getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();

    ClickType click = event.getClick();
    if (click == ClickType.CREATIVE) {
      return;
    }

    MovementMetadata movementData = meta.movement();
    int keyForward = movementData.keyForward;
    int keyStrafe = movementData.keyStrafe;

    if (movementData.simulator() == Simulators.ELYTRA) {
      return;
    }

    // Be more lenient when a flying packet was sent
    if (movementData.inWeb || movementData.receivedFlyingPacketIn(2)) {
      return;
    }

    double distanceMoved = Hypot.fast(movementData.offsetMotionX(), movementData.offsetMotionZ());
    double distanceRequirement = player.isSneaking() ? 0.04 : 0.1;
    if ((keyForward != 0 || keyStrafe != 0) && distanceMoved > distanceRequirement) {
      String message = "performed inventory-click whilst walking";
      Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
        .forPlayer(player)
        .withMessage(message)
        .withVL(0)
        .build();
      Modules.violationProcessor().processViolation(violation);
      Synchronizer.synchronize(user, player::closeInventory);
      event.setCancelled(true);
    }
  }
}

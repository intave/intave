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

package de.jpx3.intave.check.combat;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.module.tracker.player.PlayerHandTracker;
import de.jpx3.intave.packet.event.PacketReceiveEvent;
import de.jpx3.intave.packet.wire.client.tickend.ClientTickEndWirePacket;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.InventoryMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.contract.PacketType.Client.CLIENT_TICK_END;
import static org.junit.jupiter.api.Assertions.*;

class AttackReachSnapshotTest {
  @Test
  void keepsPacketTimeReachWhenProcessingIsDelayed() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    User user = UserFactory.createTestUserFor(
      FakePlayerFactory.createPlayer((methodName, arguments) ->
        methodName.equals("getLocation") ? new Location(null, 0, 0, 0) : null
      ), ProtocolMetadata.VER_1_21_4
    );
    user.meta().abilities().modifyBaseValue("player.entity_interaction_range", 6.0D);

    AttackRaytrace.Attack pending = new AttackRaytrace.Attack(null, 1, 0, Pose.STANDING, user);
    user.meta().abilities().modifyBaseValue("player.entity_interaction_range", 3.0D);
    AttackRaytrace.Attack next = new AttackRaytrace.Attack(null, 1, 0, Pose.STANDING, user);

    assertEquals(6.0D, pending.maximumReach());
    assertEquals(3.0D, next.maximumReach());
    assertEquals(0.0D, pending.hitboxMargin());
  }

  @Test
  void remembersOnlyTheLastDisplacedSpearUntilClientTickEnd() {
    MinecraftVersion.setCurrent(new MinecraftVersion("26.3"));
    User user = UserFactory.createTestUserFor(FakePlayerFactory.createPlayer((methodName, arguments) ->
      methodName.equals("getLocation") ? new Location(null, 0, 0, 0) : null
    ), ProtocolMetadata.VER_1_21_4);
    InventoryMetadata inventory = user.meta().inventory();
    ItemStack spear = new ItemStack(Material.valueOf("IRON_SPEAR"));

    inventory.recordPreviousHeldItemForReach(spear);
    assertNotNull(inventory.previousSpearThisTick());
    assertEquals(spear.getType(), inventory.previousSpearThisTick().getType());
    inventory.recordPreviousHeldItemForReach(new ItemStack(Material.STONE));
    assertNull(inventory.previousSpearThisTick());

    inventory.recordPreviousHeldItemForReach(spear);
    var packet = new ClientTickEndWirePacket();
    var event = new PacketReceiveEvent(user, CLIENT_TICK_END, packet);
    new PlayerHandTracker().receiveClientTickEnd(user, event, packet);
    assertNull(inventory.previousSpearThisTick());
  }

  @Test
  void choosesGreaterReachOnlyWhenPriorSpearIsPresent() {
    assertTrue(AttackRaytrace.Attack.usesPreviousReach(new double[]{3.0D, 0.0D}, new double[]{5.0D, 0.3D}));
    assertFalse(AttackRaytrace.Attack.usesPreviousReach(new double[]{6.0D, 0.0D}, new double[]{5.0D, 0.3D}));
    assertFalse(AttackRaytrace.Attack.usesPreviousReach(new double[]{3.0D, 0.0D}, null));
  }
}

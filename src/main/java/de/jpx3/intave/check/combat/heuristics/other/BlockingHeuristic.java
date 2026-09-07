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

package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.entity.datawatcher.DataWatcherAccess;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.view.BlockPositionView;
import de.jpx3.intave.packet.view.PacketEventsBlockPositionView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.PunishmentMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_9;

public final class BlockingHeuristic extends ClassicHeuristic<BlockingHeuristic.BlockingMeta> {

	public BlockingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.BLOCKING, BlockingMeta.class);
	}

  @PacketSubscription(
    packetsIn = {
      ARM_ANIMATION, FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovementAndSwingPacket(PacketEvent event) {
    handleMovementAndSwingPacket(
      event.getPlayer(),
      event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      ARM_ANIMATION, FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void receiveMovementAndSwingPacket(Player player, PacketReceiveEvent event) {
    if (player == null) {
      return;
    }
    // PacketEvents calls the client's arm swing packet ANIMATION; ProtocolLib calls it
    // ARM_ANIMATION. Same packet, so the branch below stays the same.
    handleMovementAndSwingPacket(
      player,
      event.getPacketType()
        == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.ANIMATION
    );
  }

  /**
   * Engine independent handling. Beyond telling a swing apart from a movement packet the body only
   * needs the player, so the discriminator is passed in as a boolean instead of the packet.
   */
  private void handleMovementAndSwingPacket(Player player, boolean armAnimation) {
    User user = userOf(player);
    BlockingMeta meta = metaOf(user);
    SimulationEnvironment movementData = user.meta().movement();

    if (movementData.ticksPast(TELEPORT) == 0) {
      return;
    }

    if (!armAnimation) {
      meta.releasedItemAfterClientTick = false;
      meta.ticksBetweenBlockAndUnblock++;
    }
    if (meta.ventosFreundlicherBoolean) {
      meta.clientTicksBetweenBlockingToggle++;
    }
    meta.heldItemOperations = 0;
  }

  private void receiveExcludedPacket(Player player, PacketContainer packet) {
    userOf(player).ignoreNextInboundPacket();
    PacketSender.receiveClientPacketFrom(player, packet);
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE, BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();

    if (!interactionIsRelevantFor(player)) {
      return;
    }

    if (packet.getType() == PacketType.Play.Client.BLOCK_DIG) {
      EnumWrappers.PlayerDigType playerDigType = packet.getPlayerDigTypes().readSafely(0);
      handleReleaseUseItem(player, playerDigType == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM);
    } else { // BLOCK_PLACE
      Integer integer = packet.getIntegers().readSafely(0);
      handleBlockingInteraction(
        player,
        packet.getItemModifier().readSafely(0),
        integer == null ? 0 : integer
      );
    }
  }

  /**
   * PacketEvents entry point for {@link #receiveInteractionPacket(PacketEvent)}.
   * <p>
   * Both packets in this subscription are served by {@link PacketEventsBlockPositionView}: the dig
   * action for the release branch and the clicked face plus the placement's own item stack for the
   * placement branch. The gate in {@link #interactionIsRelevantFor(Player)} restricts this check to
   * a 1.8-or-below server, which is exactly the wire format where the placement packet still
   * carries the held item and encodes "no block clicked" as face 255, so both fields line up with
   * what the ProtocolLib body reads out of the packet.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      BLOCK_PLACE, BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(PacketReceiveEvent event) {
    PacketEventsBlockPositionView view = PacketEventsBlockPositionView.of(event);
    if (view == null || view.player() == null) {
      return;
    }
    try {
      Player player = view.player();
      if (!interactionIsRelevantFor(player)) {
        return;
      }
      if (view.kind() == BlockPositionView.Kind.BLOCK_DIG) {
        handleReleaseUseItem(player, view.digAction() == BlockPositionView.DigAction.RELEASE_USE_ITEM);
      } else if (view.kind() == BlockPositionView.Kind.BLOCK_PLACE) {
        // Spelled out rather than left as a bare else: the bare use item packet is a member of this
        // view family and reports face 255, which is the marker the placement branch flags on, and
        // the ProtocolLib subscription can never receive that packet.
        handleBlockingInteraction(player, view.placementItem(), view.enumDirection());
      }
    } finally {
      view.release();
    }
  }

  /**
   * Engine independent gate shared by both entry points. Kept ahead of every packet field read so
   * neither engine touches the packet for a player this check does not apply to.
   */
  private boolean interactionIsRelevantFor(Player player) {
    User user = userOf(player);
    // Touched for its side effect of creating the metadata, exactly like the original body did
    // before the gate below.
    metaOf(user);
    return user.meta().protocol().emptyFlyingPacketsAreExplicitlySent()
      && !user.meta().abilities().ignoringMovementPackets()
      && user.meta().movement().ticksPast(TELEPORT) >= 10;
  }

  /** Engine independent body of the BLOCK_DIG branch; only the dig action is read off the packet. */
  private void handleReleaseUseItem(Player player, boolean releaseUseItem) {
    if (!releaseUseItem) {
      return;
    }
    User user = userOf(player);
    PunishmentMetadata punishmentData = user.meta().punishment();
    BlockingMeta meta = metaOf(user);

    meta.releasedItemAfterClientTick = true;
    meta.ventosFreundlicherBoolean = true;

    int ticksBetweenBlockAndUnblock = meta.ticksBetweenBlockAndUnblock;
    if (ticksBetweenBlockAndUnblock == 0) {
      flag(user, "unblocked too quickly", ticksBetweenBlockAndUnblock + " ticks");
      //dmc6
      user.nerf(AttackNerfStrategy.BLOCKING, "block:speed");
      punishmentData.timeLastBlockCancel = System.currentTimeMillis();
      Synchronizer.synchronize(user, () -> DataWatcherAccess.setDataWatcherFlag(player, DataWatcherAccess.WATCHER_BLOCKING_ID, false));
    }
  }

  /**
   * Engine independent body of the BLOCK_PLACE branch. The only packet fields it needs are the item
   * the placement carries and the clicked face, where 255 is the "empty interaction" marker a 1.8
   * client sends when it right clicks air - which is what a sword block looks like on the wire.
   */
  private void handleBlockingInteraction(Player player, ItemStack itemInHand, int enumDirection) {
    User user = userOf(player);
    BlockingMeta meta = metaOf(user);

    boolean sword = itemInHand != null && itemInHand.getType().name().endsWith("_SWORD");

    if (meta.releasedItemAfterClientTick) {
      String item = itemInHand == null ? "null" : itemInHand.getType().toString();
      flag(user, "sent multiple blocking interactions per tick", "item: " + item);
      user.nerf(AttackNerfStrategy.BLOCKING, "block:multiple");
    }

    int clientTicksBetweenBlockingToggle = meta.clientTicksBetweenBlockingToggle;
    if (enumDirection == 255 && meta.ventosFreundlicherBoolean && sword) {
      meta.clientTicksBetweenBlockingToggle = 0;
      meta.ventosFreundlicherBoolean = false;

      if (clientTicksBetweenBlockingToggle == 0 && meta.acaBlockingVL < 20) {
        meta.acaBlockingVL++;
        if (meta.acaBlockingVL > 2) {
          flag(user, "sent too few packets between block-toggle packets", "vl: " + meta.acaBlockingVL);
          user.nerf(AttackNerfStrategy.BLOCKING, "block:packets");
        }
      } else if (meta.acaBlockingVL > 1) {
        meta.acaBlockingVL -= 2;
      }
    }

    meta.ticksBetweenBlockAndUnblock = 0;
  }

  //---------other-check-------------

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    handleMovementPacket(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(Player player) {
    if (player == null) {
      return;
    }
    handleMovementPacket(player);
  }

  /** Engine independent handling; the body only reads Intave's own metadata, not the packet. */
  private void handleMovementPacket(Player player) {
    User user = userOf(player);
    BlockingMeta meta = metaOf(user);
    SimulationEnvironment movementData = user.meta().movement();
    ProtocolMetadata clientData = user.meta().protocol();
    if (movementData.ticksPast(TELEPORT) < 10) {
      return;
    }
    // checks if the client version is above 1.8 for disabling the check if the player is standing still
    if (!movementData.receivedFlyingPacketIn(2) || clientData.protocolVersion() < VER_1_9) {
      if (meta.heldItemOperations > 0) {
        if (meta.blocksPlacedThisTick == 0 || meta.heldItemOperations > 2) {
          String details = "operations: " + meta.heldItemOperations
            + ", version: " + clientData.versionString();
          flag(user, "sent too many item operations", details);
          meta.unsendPackets.clear();
        }
      }
    }

//    if(meta.unsendPackets.size() != 0) {
//      PacketContainer packetContainer = meta.unsendPackets.get(0);
//      receiveExcludedPacket(player, packetContainer);
//      meta.unsendPackets.clear();
//    }

    meta.blocksPlacedThisTick = 0;
  }

  @PacketSubscription(
    packetsIn = {
      USE_ITEM
    }
  )
  public void receiveUseItem(PacketEvent event) {
    handleUseItem(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      USE_ITEM
    }
  )
  public void receiveUseItem(Player player) {
    if (player == null) {
      return;
    }
    handleUseItem(player);
  }

  /** Engine independent handling; the body only counts the packet, it never reads it. */
  private void handleUseItem(Player player) {
    User user = userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    BlockingMeta meta = metaOf(player);
    // 1.8
    if (clientData.protocolVersion() >= VER_1_9) {
      meta.blocksPlacedThisTick++;
    }
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void receiveBlockPlace(PacketEvent event) {
    handleBlockPlace(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void receiveBlockPlace(Player player) {
    if (player == null) {
      return;
    }
    handleBlockPlace(player);
  }

  /** Engine independent handling; the body only counts the packet, it never reads it. */
  private void handleBlockPlace(Player player) {
    User user = userOf(player);
    BlockingMeta meta = metaOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    // 1.9+
    if (clientData.protocolVersion() < VER_1_9) {
      meta.blocksPlacedThisTick++;
    }
  }

  @PacketSubscription(
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveHeldItemSlot(PacketEvent event) {
    handleHeldItemSlot(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      HELD_ITEM_SLOT_IN
    }
  )
  public void receiveHeldItemSlot(Player player) {
    if (player == null) {
      return;
    }
    handleHeldItemSlot(player);
  }

  /** Engine independent handling; the body only counts the packet, it never reads the new slot. */
  private void handleHeldItemSlot(Player player) {
    User user = userOf(player);
    BlockingMeta meta = metaOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (user.meta().abilities().ignoringMovementPackets()) {
      return;
    }

//    if(!movementData.recentlyEncounteredFlyingPacket(2) || clientData.protocolVersion() < VER_1_9) {
//      if (meta.heldItemOperations > 0) {
//        PacketContainer clonedPacket = event.getPacket().deepClone();
//        meta.unsendPackets.add(clonedPacket);
//        event.setCancelled(true);
//      }
//    }

    meta.heldItemOperations++;
  }

  public static final class BlockingMeta extends CheckCustomMetadata {
    private final List<PacketContainer> unsendPackets = new ArrayList<>();
    private int blocksPlacedThisTick;
    public boolean releasedItemAfterClientTick;
    public int ticksBetweenBlockAndUnblock, clientTicksBetweenBlockingToggle;
    public boolean ventosFreundlicherBoolean;

    public int acaBlockingVL;
    public int heldItemOperations;
  }
}

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

package de.jpx3.intave.check.world;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.world.interaction.Interaction;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsSender;
import de.jpx3.intave.share.Direction;
import org.bukkit.entity.Player;

/**
 * The PacketEvents side of the packet an {@link Interaction} parks.
 * <p>
 * The ProtocolLib path parks {@code packet.shallowClone()} in {@link Interaction#thePacket()} and
 * patches that container in place before handing it back to the server. PacketEvents has no
 * equivalent to clone: a wrapper decoded off an inbound event borrows the event's byte buffer, and
 * re-injecting that wrapper would write into the packet still being dispatched. So the decoded
 * field values are parked here instead and a fresh wrapper is built from them at replay time - the
 * same trade {@code PlayerHandTracker#replayDigPacket} already documents for the one-shot replay it
 * does.
 * <p>
 * Deliberately <em>not</em> reachable from {@link Interaction}: that class and the whole
 * {@code check.world.interaction} package stay typed on ProtocolLib for the ProtocolLib path, which
 * has to keep working bit for bit. {@link InteractionRaytrace.InteractionMeta} keeps the parked
 * packets in a side map keyed by {@link Interaction#interactionId()} instead, so a PacketEvents
 * interaction travels through the identical routing code and only the two lines that touch the
 * packet - the patch and the re-injection - branch on whether a parked packet exists.
 *
 * <h2>Field mapping</h2>
 * Every field the wrapper decodes is parked, including the ones no check reads (the pre-1.9 item
 * stack, the 1.19 inside-block flag), because the replayed packet has to be the packet the client
 * sent and not a reconstruction of the parts Intave happens to care about. The clicked face is
 * parked as its raw wire id as well as its {@link BlockFace}: the id is what
 * {@code WrapperPlayClientPlayerBlockPlacement#write} serialises, and re-deriving it from the
 * enum constant would collapse every non-cartesian byte onto whatever value {@code OTHER} carries.
 */
public final class PacketEventsInteractionPacket {

  private final boolean digging;

  /** Placement only. */
  private final InteractionHand hand;
  private final Vector3f cursorPosition;
  private final ItemStack itemStack;
  private final Boolean insideBlock;

  /** Digging only. */
  private final DiggingAction action;

  private Vector3i blockPosition;
  private BlockFace face;
  private int faceId;
  private final int sequence;

  private PacketEventsInteractionPacket(
    boolean digging,
    @Nullable InteractionHand hand,
    @Nullable Vector3f cursorPosition,
    @Nullable ItemStack itemStack,
    @Nullable Boolean insideBlock,
    @Nullable DiggingAction action,
    Vector3i blockPosition,
    BlockFace face,
    int faceId,
    int sequence
  ) {
    this.digging = digging;
    this.hand = hand;
    this.cursorPosition = cursorPosition;
    this.itemStack = itemStack;
    this.insideBlock = insideBlock;
    this.action = action;
    this.blockPosition = blockPosition;
    this.face = face;
    this.faceId = faceId;
    this.sequence = sequence;
  }

  /** Parks the block placement / use item on packet the given wrapper decoded. */
  public static PacketEventsInteractionPacket parkPlacement(WrapperPlayClientPlayerBlockPlacement wrapper) {
    return new PacketEventsInteractionPacket(
      false,
      wrapper.getHand(),
      wrapper.getCursorPosition(),
      wrapper.getItemStack().orElse(null),
      wrapper.getInsideBlock().orElse(null),
      null,
      wrapper.getBlockPosition(),
      wrapper.getFace(),
      wrapper.getFaceId(),
      wrapper.getSequence()
    );
  }

  /** Parks the player digging packet the given wrapper decoded. */
  public static PacketEventsInteractionPacket parkDigging(WrapperPlayClientPlayerDigging wrapper) {
    BlockFace face = wrapper.getBlockFace();
    return new PacketEventsInteractionPacket(
      true,
      null, null, null, null,
      wrapper.getAction(),
      wrapper.getBlockPosition(),
      face,
      face == null ? 0 : face.getFaceValue(),
      wrapper.getSequence()
    );
  }

  /**
   * Twin of {@code InteractionRaytrace#writeBlockPosition} plus
   * {@code InteractionRaytrace#writeEnumDirection}: overwrites the clicked block and the clicked
   * face with the ray traced ones.
   * <p>
   * One method rather than two because the ProtocolLib pair only exists as a pair - it is called
   * once, with both values, from the single site that patches a parked packet - and because on
   * PacketEvents both values are plain wrapper fields, so neither needs the 1.14 hit-result branch
   * the ProtocolLib writers carry.
   */
  public void patch(int blockX, int blockY, int blockZ, Direction direction) {
    this.blockPosition = new Vector3i(blockX, blockY, blockZ);
    BlockFace patchedFace = faceOf(direction);
    if (patchedFace != null) {
      this.face = patchedFace;
      this.faceId = direction.getIndex();
    }
  }

  /**
   * Hands the parked packet back to the server, the twin of
   * {@code InteractionRaytrace#receiveExcludedPacket}.
   * <p>
   * The caller owns {@code User#ignoreNextInboundPacket()} and sets it immediately before this
   * call, exactly as the ProtocolLib method does;
   * {@link PacketEventsSender#receiveClientPacketFrom} documents why.
   */
  public void replayTo(Player player) {
    PacketEventsSender.receiveClientPacketFrom(player, buildWrapper());
  }

  private PacketWrapper<?> buildWrapper() {
    if (digging) {
      return new WrapperPlayClientPlayerDigging(action, blockPosition, face, sequence);
    }
    WrapperPlayClientPlayerBlockPlacement placement = new WrapperPlayClientPlayerBlockPlacement(
      hand, blockPosition, face, cursorPosition, itemStack, insideBlock, sequence
    );
    // The constructor derives the wire id from the enum constant; restore the id that was actually
    // on the wire so a face byte outside the six cartesian ones round trips unchanged.
    placement.setFaceId(faceId);
    return placement;
  }

  /**
   * Names the packet the way {@code InteractionRaytrace#receiveExcludedPacket} names a
   * {@link com.comphenix.protocol.events.PacketContainer} in the routing debug line: the packet
   * kind, then the concrete wrapper the replay builds.
   */
  @Override
  public String toString() {
    return digging
      ? "BLOCK_DIG " + WrapperPlayClientPlayerDigging.class.getSimpleName()
      : "BLOCK_PLACE " + WrapperPlayClientPlayerBlockPlacement.class.getSimpleName();
  }

  /**
   * Maps by name rather than by ordinal. Both enums are ordered D-U-N-S-W-E and PacketEvents'
   * face value for those six is that index, but the ProtocolLib writers this mirrors name their
   * constants explicitly too, and a silent reordering on either side would move a placement onto
   * the wrong block face.
   */
  private static @Nullable BlockFace faceOf(@Nullable Direction direction) {
    if (direction == null) {
      return null;
    }
    switch (direction) {
      case DOWN:
        return BlockFace.DOWN;
      case UP:
        return BlockFace.UP;
      case NORTH:
        return BlockFace.NORTH;
      case SOUTH:
        return BlockFace.SOUTH;
      case WEST:
        return BlockFace.WEST;
      case EAST:
        return BlockFace.EAST;
      default:
        return null;
    }
  }
}

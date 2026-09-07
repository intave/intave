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

package de.jpx3.intave.packet.view;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;

import java.util.Optional;

/**
 * A parked inbound packet for {@code AttackRaytrace}'s hold and replay machine on the PacketEvents
 * engine.
 * <p>
 * Not a {@code View}: the view family exposes what a check <em>reads</em> off a live packet, while
 * this class exists to survive the packet. It sits in this package anyway because it is the
 * PacketEvents half of the same seam - {@code PacketEventsAttackView} answers "what did the player
 * do", this answers "how do I say it again later".
 *
 * <h2>Why fields and not a buffer</h2>
 * A PacketEvents subscription is handed a wrapper over the connection's inbound {@code ByteBuf},
 * which PacketEvents recycles as soon as the listener returns; it cannot be held across ticks the
 * way ProtocolLib's {@code PacketContainer#shallowClone()} can. Both packets the machine parks are
 * small and fully described by a handful of decoded values, so those values are kept and a fresh
 * wrapper is built from them in {@link #rebuild()}.
 *
 * <h2>Faithfulness of the round trip</h2>
 * Every field {@code WrapperPlayClientInteractEntity#write()} can emit is captured, including the
 * ones that only exist on some protocol versions: the interact-at hit vector (written only for
 * {@code INTERACT_AT}), the hand (1.9 and newer, and only for the two interact actions) and the
 * sneaking flag (1.16 and newer). The rebuilt wrapper therefore encodes byte for byte what the
 * client sent, rather than a normalised approximation of it. The same holds for the animation
 * packet, whose only payload is the hand.
 */
public final class AttackRaytraceReplay {

  private final boolean animation;
  private final int entityId;
  private final WrapperPlayClientInteractEntity.InteractAction action;
  private final InteractionHand hand;
  private final Optional<Vector3f> target;
  private final Optional<Boolean> sneaking;

  private AttackRaytraceReplay(
    boolean animation,
    int entityId,
    WrapperPlayClientInteractEntity.InteractAction action,
    InteractionHand hand,
    Optional<Vector3f> target,
    Optional<Boolean> sneaking
  ) {
    this.animation = animation;
    this.entityId = entityId;
    this.action = action;
    this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    this.target = target == null ? Optional.empty() : target;
    this.sneaking = sneaking == null ? Optional.empty() : sneaking;
  }

  /**
   * @return the decoded entity interaction, or null when the event does not carry one. PacketEvents
   * has no separate attack packet: both {@code ATTACK} and {@code USE_ENTITY} resolve to
   * {@code INTERACT_ENTITY}, exactly as {@link PacketEventsAttackView} documents.
   */
  public static AttackRaytraceReplay ofInteraction(PacketReceiveEvent event) {
    if (event == null || event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
      return null;
    }
    WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
    return new AttackRaytraceReplay(
      false,
      wrapper.getEntityId(),
      wrapper.getAction(),
      wrapper.getHand(),
      wrapper.getTarget(),
      wrapper.isSneaking()
    );
  }

  /** @return the decoded arm animation, or null when the event does not carry one. */
  public static AttackRaytraceReplay ofAnimation(PacketReceiveEvent event) {
    if (event == null || event.getPacketType() != PacketType.Play.Client.ANIMATION) {
      return null;
    }
    WrapperPlayClientAnimation wrapper = new WrapperPlayClientAnimation(event);
    return new AttackRaytraceReplay(
      true, 0, null, wrapper.getHand(), Optional.empty(), Optional.empty()
    );
  }

  /** @return the interacted entity's runtime id; meaningless for a parked animation. */
  public int entityId() {
    return entityId;
  }

  /**
   * Builds a packet equivalent to the one that was parked.
   * <p>
   * A fresh wrapper every time: the caller hands it to PacketEvents, which encodes it into a buffer
   * it then owns, so a wrapper must not be reused for a second replay.
   */
  public PacketWrapper<?> rebuild() {
    if (animation) {
      return new WrapperPlayClientAnimation(hand);
    }
    return new WrapperPlayClientInteractEntity(entityId, action, hand, target, sneaking);
  }
}

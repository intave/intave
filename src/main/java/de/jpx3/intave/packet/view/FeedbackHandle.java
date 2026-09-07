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

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Nullable;
import org.bukkit.entity.Player;

/**
 * Engine neutral view of "the packet a tick-feedback request is attached to".
 * <p>
 * The single-shot feedback entry points ({@code packetTickFeedback} and
 * {@code tracedPacketTickFeedback}) used to take a raw ProtocolLib {@code PacketEvent}, which made
 * them unreachable from a PacketEvents subscription. Reading what the feedback module actually
 * does with that event shows the dependency is much smaller than the signature suggests: the event
 * is forwarded verbatim as the {@code toBundle} argument of
 * {@code FeedbackSender#tracedSingleSynchronize}, where it is only consulted inside the one
 * optional branch that folds the transaction packet and the observed packet into a single 1.19.4+
 * {@code BUNDLE} container. That branch is already skipped on older protocols and whenever
 * {@code check.physics.no-bundling} is set, and {@code toBundle} is declared {@code @Nullable}
 * precisely so it can be skipped - the transaction is then simply sent on its own and the observed
 * packet is left alone.
 * <p>
 * So the minimum operation a backend has to provide is: "give me a ProtocolLib bundling target, or
 * admit you have none". That is what {@link #bundleTarget()} is. ProtocolLib returns its event and
 * keeps the bundling path bit for bit; PacketEvents returns {@code null} and takes the unbundled
 * path the ProtocolLib backend itself takes on every server below 1.19.4.
 *
* <h2>Why this handle deliberately carries no packet replay operation</h2>
 * The double-sandwich entry points ({@code doubleTickFeedback} and
 * {@code doubleTracedTickFeedback}) need strictly more than a bundling target: they cancel the
 * original event and re-emit the packet between the two transactions <em>without</em> re-entering
 * the listener chain. Both engines can do that, but through calls of different shapes - ProtocolLib
 * re-sends a {@code shallowClone()} of the {@code PacketContainer} through
 * {@code PacketSender#sendServerPacketWithoutEvent}, PacketEvents writes the encoded bytes of
 * {@code ProtocolPacketEvent#getFullBufferClone()} through
 * {@code ProtocolManager#sendPacketSilently(channel, buffer)}, its raw and silent send. Neither
 * shape reduces to "give me a bundling target", which is all this handle is, so the double variants
 * take a per-engine overload instead: {@code User#doubleTickFeedback(PacketEvent, ...)} and
 * {@code User#doubleTickFeedback(PacketSendEvent, ...)}. Both land in the same sandwich body in
 * {@code FeedbackSender}, which differs between them only in that one re-send call.
 * <p>
 * Note for anyone reading an older revision of this file: the PacketEvents half was previously
 * recorded here as impossible, on the grounds that {@code User#sendPacket(Object)} re-enters the
 * encoder and {@code User#sendPacketSilently} takes a decoded {@code PacketWrapper} only. Both of
 * those statements are true; what they missed is that {@code ProtocolManager} - and its
 * {@code PlayerManager} facade - also carry a raw {@code sendPacketSilently(Object, Object)} that
 * takes an already encoded buffer. That is the call the sandwich needs, and it is present in
 * PacketEvents 2.4.0.
  */
public interface FeedbackHandle {

  /**
   * @return the player the observed packet belongs to, or null when the connection has not reached
   * a state where a Bukkit player exists yet.
   */
  @Nullable
  Player player();

  /**
   * @return the ProtocolLib event whose packet the transaction may be bundled with, or null when
   * this backend cannot supply a {@code PacketContainer}. A null answer is a supported, normal
   * input for the feedback module and only means the transaction is sent unbundled.
   */
  @Nullable
  PacketEvent bundleTarget();
}

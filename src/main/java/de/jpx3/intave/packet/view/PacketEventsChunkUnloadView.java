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

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUnloadChunk;
import de.jpx3.intave.annotate.Nullable;
import org.bukkit.entity.Player;

/**
 * {@link ChunkUnloadView} backed by PacketEvents.
 * <p>
 * The two shapes the ProtocolLib view has to tell apart do not exist here: PacketEvents decodes the
 * packed chunk key and the legacy two-var-int form into the same pair of integers on every version,
 * so the coordinate is always present once the wrapper decoded. Unlike the chunk <i>data</i> packet
 * this one is tiny - there is no column to decode - so building the wrapper costs nothing worth
 * weighing.
 */
public final class PacketEventsChunkUnloadView implements ChunkUnloadView {

  private final PacketSendEvent event;
  private final int chunkX;
  private final int chunkZ;

  private PacketEventsChunkUnloadView(PacketSendEvent event, int chunkX, int chunkZ) {
    this.event = event;
    this.chunkX = chunkX;
    this.chunkZ = chunkZ;
  }

  /** @return a view over the event, or null when the packet is not an unload chunk packet. */
  public static @Nullable PacketEventsChunkUnloadView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.UNLOAD_CHUNK) {
      return null;
    }
    WrapperPlayServerUnloadChunk wrapper = new WrapperPlayServerUnloadChunk(event);
    return new PacketEventsChunkUnloadView(event, wrapper.getChunkX(), wrapper.getChunkZ());
  }

  /** @return the Bukkit player, or null before the connection reaches the play phase. */
  public @Nullable Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public int chunkX() {
    return chunkX;
  }

  @Override
  public int chunkZ() {
    return chunkZ;
  }
}

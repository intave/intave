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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import de.jpx3.intave.annotate.Nullable;

/**
 * {@link ChunkUnloadView} backed by ProtocolLib.
 * <p>
 * No reader is involved: the chunk tracker always read this packet straight off the
 * {@link PacketContainer}, and the two shapes it can take are resolved here instead. The packed
 * chunk key is exposed as a {@code ChunkCoordIntPair} on the versions ProtocolLib has the wrapper
 * for, and as two plain integers on the rest, so both are tried in that order - exactly the order
 * the tracker used before this view existed.
 * <p>
 * The coordinates are read once at construction, so nothing here is bound to the packet's lifetime.
 */
public final class ProtocolLibChunkUnloadView implements ChunkUnloadView {

  private final int chunkX;
  private final int chunkZ;

  private ProtocolLibChunkUnloadView(int chunkX, int chunkZ) {
    this.chunkX = chunkX;
    this.chunkZ = chunkZ;
  }

  /**
   * @return a view over the packet, or null when neither shape carries a readable coordinate, which
   * is the case the tracker has always silently ignored.
   */
  public static @Nullable ProtocolLibChunkUnloadView of(PacketContainer packet) {
    ChunkCoordIntPair coordinates = packet.getChunkCoordIntPairs().readSafely(0);
    if (coordinates != null) {
      return new ProtocolLibChunkUnloadView(coordinates.getChunkX(), coordinates.getChunkZ());
    }
    Integer chunkX = packet.getIntegers().readSafely(0);
    Integer chunkZ = packet.getIntegers().readSafely(1);
    if (chunkX != null && chunkZ != null) {
      return new ProtocolLibChunkUnloadView(chunkX, chunkZ);
    }
    return null;
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

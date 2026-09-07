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

/**
 * Engine neutral view of the outbound unload chunk packet.
 * <p>
 * The counterpart of the {@link BlockPositionView#kind() CHUNK_DATA} kind: where that one announces
 * a chunk to the client, this one retracts it. The chunk tracker reads nothing but the chunk
 * coordinate off it, so that is the only thing exposed here.
 * <p>
 * Both backends normalise the wire differences away. The packet carries a single packed chunk key
 * on modern versions and two var ints on legacy ones, and ProtocolLib surfaces that as either a
 * {@code ChunkCoordIntPair} or as two plain integers depending on the server version, so a view is
 * only handed out once the coordinate was actually found - see the {@code of} factory of either
 * implementation, which returns null when the packet carries no readable coordinate.
 */
public interface ChunkUnloadView {

  /** @return the x coordinate of the chunk the client is told to drop. */
  int chunkX();

  /** @return the z coordinate of the chunk the client is told to drop. */
  int chunkZ();
}

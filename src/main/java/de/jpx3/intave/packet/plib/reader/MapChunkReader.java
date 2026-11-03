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

package de.jpx3.intave.packet.plib.reader;

public final class MapChunkReader extends AbstractPacketReader implements ChunkCoordinateReader {
  @Override
  public int[] xCoordinates() {
    return new int[]{packet().getIntegers().read(0)};
  }

  @Override
  public int[] zCoordinates() {
    return new int[]{packet().getIntegers().read(1)};
  }
}

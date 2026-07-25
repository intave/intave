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

package de.jpx3.intave.packet.reader;

import de.jpx3.intave.share.BlockPosition;
import org.bukkit.Material;

import static de.jpx3.intave.share.BlockPosition.*;

public final class BlockActionReader extends AbstractPacketReader {
  public BlockPosition blockPosition() {
    return fromProtocolLib(packet().getBlockPositionModifier().read(0));
  }

  public Material blockType() {
    return packet().getBlocks().read(0);
  }

  public int action() {
    return packet().getIntegers().read(0);
  }

  public int data() {
    return packet().getIntegers().read(1);
  }
}
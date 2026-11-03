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

import com.comphenix.protocol.events.PacketContainer;

public abstract class CompiledPacketReader extends AbstractPacketReader {
  @Override
  public void enter(PacketContainer packet) {
    super.enter(packet);
    compile();
  }

  public abstract void compile();
}

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

import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.ClientCommand;

public final class ClientCommandReader extends AbstractPacketReader {
  public @Nullable ClientCommand command() {
    EnumWrappers.ClientCommand command = packet().getClientCommands().readSafely(0);
    if (command == null) {
      return null;
    }
    return command == EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT
      ? ClientCommand.OPEN_INVENTORY
      : ClientCommand.valueOf(command.name());
  }

  public void setCommand(ClientCommand command) {
    EnumWrappers.ClientCommand value = command == ClientCommand.OPEN_INVENTORY
      ? EnumWrappers.ClientCommand.OPEN_INVENTORY_ACHIEVEMENT
      : EnumWrappers.ClientCommand.valueOf(command.name());
    packet().getClientCommands().write(0, value);
  }
}

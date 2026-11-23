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

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packetperfect.OnReceive;
import de.jpx3.intave.packet.contract.immutable.ClientSettingsPacket;
import de.jpx3.intave.packet.contract.immutable.PayloadInPacket;
import de.jpx3.intave.user.User;
import org.bukkit.event.Cancellable;

public final class SettingsTracker extends Module {
  @OnReceive
  public void receiveClientOptions(User user, ClientSettingsPacket packet, Cancellable cancellable) {
    if (cancellable.isCancelled()) {
      return;
    }
    user.meta().protocol().setLocale(packet.locale());
  }

  @OnReceive
  public void receivePayloadPacket(User user, PayloadInPacket packet, Cancellable cancellable) {
    if (cancellable.isCancelled()) {
      return;
    }
    packet.clientBrand().ifPresent(user.meta().protocol()::setClientBrand);
  }
}

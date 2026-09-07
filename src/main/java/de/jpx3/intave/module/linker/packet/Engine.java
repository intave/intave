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

package de.jpx3.intave.module.linker.packet;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public enum Engine {
  PROTOCOLLIB,
  INTERNAL,
  /**
   * Packet delivery through PacketEvents. Subscriber methods bound to this engine receive
   * PacketEvents types ({@code PacketReceiveEvent} / {@code PacketSendEvent}) instead of
   * ProtocolLib's {@code PacketEvent}.
   */
  PACKETEVENTS
}

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

public class AbilityOutReader extends AbstractPacketReader {
  public float flyingSpeed() {
    return packet().getFloat().read(0);
  }

  public float walkingSpeed() {
    return packet().getFloat().read(1);
  }

  public boolean flying() {
    return packet().getBooleans().read(1);
  }

  public boolean flyingAllowed() {
    return packet().getBooleans().read(2);
  }
}

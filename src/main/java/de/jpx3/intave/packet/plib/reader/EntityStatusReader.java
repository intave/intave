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

import de.jpx3.intave.annotate.Nullable;

public final class EntityStatusReader extends EntityReader {
  private static final byte ITEM_USE_FINISHED = 9;
  private static final byte TOTEM_ACTIVATION = 35;

  public @Nullable Byte status() {
    return packet().getBytes().readSafely(0);
  }

  public boolean indicatesItemUseFinished() {
    Byte status = status();
    return status != null && status == ITEM_USE_FINISHED;
  }

  public boolean indicatesTotemActivation() {
    Byte status = status();
    return status != null && status == TOTEM_ACTIVATION;
  }
}

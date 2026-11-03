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

public final class TransactionReader extends AbstractPacketReader {
  public @Nullable Integer containerId() {
    return packet().getIntegers().readSafely(0);
  }

  public @Nullable Short actionNumber() {
    return packet().getShorts().readSafely(0);
  }

  public @Nullable Boolean accepted() {
    return packet().getBooleans().readSafely(0);
  }

  public boolean isRejected() {
    return Boolean.FALSE.equals(accepted());
  }
}

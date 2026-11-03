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
package de.jpx3.intave.library.asm.tree.analysis;

/**
 * An immutable symbolic value for the semantic interpretation of bytecode.
 *
 * @author Eric Bruneton
 */
public interface Value {

  /**
   * Returns the size of this value in 32 bits words. This size should be 1 for byte, boolean, char,
   * short, int, float, object and array types, and 2 for long and double.
   *
   * @return either 1 or 2.
   */
  int getSize();
}

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
package de.jpx3.intave.library.asm.commons;

import de.jpx3.intave.library.asm.Label;

/**
 * A code generator for switch statements.
 *
 * @author Juozas Baliuka
 * @author Chris Nokleberg
 * @author Eric Bruneton
 */
public interface TableSwitchGenerator {

  /**
   * Generates the code for a switch case.
   *
   * @param key the switch case key.
   * @param end a label that corresponds to the end of the switch statement.
   */
  void generateCase(int key, Label end);

  /**
   * Generates the code for the default switch case.
   */
  void generateDefault();
}

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
package de.jpx3.intave.library.asm.util;

import de.jpx3.intave.library.asm.Attribute;
import de.jpx3.intave.library.asm.Label;

import java.util.Map;

/**
 * An {@link Attribute} that can generate the ASM code to create an equivalent
 * attribute.
 *
 * @author Eugene Kuleshov
 */
// DontCheck(AbbreviationAsWordInName): can't be renamed (for backward binary compatibility).
public interface ASMifierSupport {

  /**
   * Generates the ASM code to create an attribute equal to this attribute.
   *
   * @param outputBuilder       where the generated code must be appended.
   * @param visitorVariableName the name of the visitor variable in the produced code.
   * @param labelNames          the names of the labels in the generated code.
   */
  void asmify(
    StringBuilder outputBuilder, String visitorVariableName, Map<Label, String> labelNames);
}

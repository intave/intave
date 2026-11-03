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
 * An {@link Attribute} that can print a readable representation of itself.
 *
 * @author Eugene Kuleshov
 */
public interface TextifierSupport {

  /**
   * Generates a human readable representation of this attribute.
   *
   * @param outputBuilder where the human representation of this attribute must be appended.
   * @param labelNames    the human readable names of the labels.
   */
  void textify(StringBuilder outputBuilder, Map<Label, String> labelNames);
}

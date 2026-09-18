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

package de.jpx3.intave.adapter;

// thrown when a component jar is ready on disk but the server refuses to load it
// before the next restart (modern paper bans paper plugins loaded at runtime)
public final class ComponentRestartRequiredException extends RuntimeException {
  private final String componentName;
  private final String fileName;

  public ComponentRestartRequiredException(String componentName, String fileName, Throwable cause) {
    super("component " + componentName + " needs a server restart to load", cause);
    this.componentName = componentName;
    this.fileName = fileName;
  }

  public String componentName() {
    return componentName;
  }

  public String fileName() {
    return fileName;
  }
}

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

package de.jpx3.intave.packet.view;

import de.jpx3.intave.packet.reader.UpdateTagReader;
import de.jpx3.intave.share.MinecraftKey;

import java.util.List;
import java.util.Map;

/**
 * {@link UpdateTagView} backed by ProtocolLib.
 * <p>
 * Like {@link ProtocolLibPayloadInView} this wraps the pooled {@link UpdateTagReader} the
 * subscription linker already injected, so the reader keeps its existing lifecycle: the linker
 * releases it once the subscription returns and nothing here has to.
 */
public final class ProtocolLibUpdateTagView implements UpdateTagView {

  private final UpdateTagReader reader;

  public ProtocolLibUpdateTagView(UpdateTagReader reader) {
    this.reader = reader;
  }

  /** @return the wrapped reader, for tag code that still needs ProtocolLib specifics. */
  public UpdateTagReader reader() {
    return reader;
  }

  @Override
  public Map<MinecraftKey, List<MinecraftKey>> readTags() {
    return reader.readTags();
  }
}

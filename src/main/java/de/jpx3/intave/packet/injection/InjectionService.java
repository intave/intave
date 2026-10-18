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

package de.jpx3.intave.packet.injection;

import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.packet.injection.pipeline.DefaultPipelineInjector;
import de.jpx3.intave.packet.injection.pipeline.PaperPipelineInjector;
import de.jpx3.intave.packet.injection.pipeline.PipelineInjector;

import java.util.concurrent.atomic.AtomicBoolean;

public final class InjectionService {
  private final AtomicBoolean closed = new AtomicBoolean();
  private final PipelineInjector pipelineInjector;

  public InjectionService() {
    PipelineInjector fallback = new DefaultPipelineInjector();
    this.pipelineInjector = new PaperPipelineInjector(fallback);
    this.pipelineInjector.inject();
    ShutdownTasks.add(this::close);
  }

  private void close() {
    if (!this.closed.compareAndSet(false, true)) {
      return;
    }
    this.pipelineInjector.uninject();
  }
}

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

package de.jpx3.intave.packet.injection.pipeline;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.packet.injection.Binding;
import io.netty.channel.Channel;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class ChannelPipelineInjector implements PipelineInjector {
  private final Set<Binding> connectionBindings = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean injected = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();

  @Override
  public final void inject() {
    if (this.closed.get()) {
      throw new IllegalStateException(getClass().getSimpleName() + " is closed");
    }
    if (!this.injected.compareAndSet(false, true)) {
      return;
    }
    try {
      injectServerChannels();
    } catch (RuntimeException failure) {
      uninject();
      throw failure;
    }
  }

  protected abstract void injectServerChannels();

  @Override
  public final void uninject() {
    if (!this.closed.compareAndSet(false, true)) {
      return;
    }
    try {
      uninjectServerChannels();
    } finally {
      for (Binding binding : this.connectionBindings) {
        binding.close();
      }
      this.connectionBindings.clear();
    }
  }

  protected abstract void uninjectServerChannels();

  protected final boolean isClosed() {
    return this.closed.get();
  }

  protected final void injectConnectionAfterInitializers(Channel channel) {
    channel.eventLoop().execute(() -> injectConnection(channel));
  }

  protected final void injectConnection(Channel channel) {
    if (this.closed.get() || !channel.isOpen()) {
      return;
    }

    final Binding binding;
    try {
      binding = Binding.bind(channel);
    } catch (RuntimeException failure) {
      IntaveLogger.logger().error("Failed to inject connection into channel " + channel, failure);
      return;
    }

    this.connectionBindings.add(binding);
    if (this.closed.get()) {
      this.connectionBindings.remove(binding);
      binding.close();
      return;
    }
    channel.closeFuture().addListener(ignored -> {
      this.connectionBindings.remove(binding);
      binding.close();
    });
  }
}

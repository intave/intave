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
import de.jpx3.intave.reflect.ServerConnection;
import io.netty.channel.*;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultPipelineInjector extends ChannelPipelineInjector {
  private static final String ACCEPTOR_NAME = "intave-channel-acceptor";
  private static final String INSTALLER_NAME = "intave-channel-installer";

  private final Set<Channel> listeningChannels = ConcurrentHashMap.newKeySet();
  private final ChannelHandler acceptedConnectionHandler = new AcceptedConnectionHandler();

  @Override
  protected void injectServerChannels() {
    for (Channel channel : ServerConnection.find().listeningChannels()) {
      injectListeningChannel(channel);
    }
    if (this.listeningChannels.isEmpty()) {
      throw new IllegalStateException("Could not find Minecraft's listening channel");
    }
  }

  @Override
  protected void uninjectServerChannels() {
    for (Channel channel : new ArrayList<>(this.listeningChannels)) {
      uninjectListeningChannel(channel);
    }
    this.listeningChannels.clear();
  }

  private void injectListeningChannel(Channel channel) {
    runOnEventLoop(channel, () -> {
      if (isClosed()) {
        return;
      }
      ChannelPipeline pipeline = channel.pipeline();
      ChannelHandler existing = pipeline.get(ACCEPTOR_NAME);
      if (existing != null && existing != this.acceptedConnectionHandler) {
        throw new IllegalStateException("Another handler already uses " + ACCEPTOR_NAME);
      }
      if (existing == null) {
        pipeline.addFirst(ACCEPTOR_NAME, this.acceptedConnectionHandler);
      }
      this.listeningChannels.add(channel);
    });
  }

  private void uninjectListeningChannel(Channel channel) {
    try {
      runOnEventLoop(channel, () -> {
        ChannelPipeline pipeline = channel.pipeline();
        ChannelHandler existing = pipeline.get(ACCEPTOR_NAME);
        if (existing == this.acceptedConnectionHandler) {
          pipeline.remove(ACCEPTOR_NAME);
        }
        this.listeningChannels.remove(channel);
      });
    } catch (RuntimeException failure) {
      IntaveLogger.logger().error("Could not unhook Minecraft listening channel " + channel, failure);
    }
  }

  private static void runOnEventLoop(Channel channel, Runnable task) {
    if (channel.eventLoop().inEventLoop()) {
      task.run();
    } else {
      channel.eventLoop().submit(task).syncUninterruptibly();
    }
  }

  @ChannelHandler.Sharable
  private final class AcceptedConnectionHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext context, Object message) {
      if (message instanceof Channel && !isClosed()) {
        Channel channel = (Channel) message;
        if (channel.pipeline().get(INSTALLER_NAME) == null) {
          channel.pipeline().addFirst(INSTALLER_NAME, new InstallAfterInitializationHandler());
        }
      }
      context.fireChannelRead(message);
    }
  }

  private final class InstallAfterInitializationHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext context) {
      try {
        context.fireChannelActive();
        injectConnection(context.channel());
      } finally {
        if (context.pipeline().context(this) != null) {
          context.pipeline().remove(this);
        }
      }
    }
  }
}

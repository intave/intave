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
import io.netty.channel.Channel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

public final class PaperPipelineInjector extends ChannelPipelineInjector {
  private static final String PAPER_LISTENER = "io.papermc.paper.network.ChannelInitializeListener";
  private static final String PAPER_LISTENER_HOLDER = "io.papermc.paper.network.ChannelInitializeListenerHolder";
  private static final String ADVENTURE_KEY = "net.kyori.adventure.key.Key";

  private final PipelineInjector fallback;
  private Object listenerKey;
  private boolean fallbackActive;

  public PaperPipelineInjector(PipelineInjector fallback) {
    this.fallback = Objects.requireNonNull(fallback, "fallback");
  }

  @Override
  protected void injectServerChannels() {
    if (!registerListener()) {
      this.fallbackActive = true;
      this.fallback.inject();
    }
  }

  @Override
  protected void uninjectServerChannels() {
    if (this.fallbackActive) {
      this.fallback.uninject();
      return;
    }
    unregisterListener();
  }

  private boolean registerListener() {
    final Class<?> listenerType;
    final Class<?> listenerHolderType;
    final Class<?> keyType;
    try {
      listenerType = Class.forName(PAPER_LISTENER);
      listenerHolderType = Class.forName(PAPER_LISTENER_HOLDER);
      keyType = Class.forName(ADVENTURE_KEY);
    } catch (ClassNotFoundException unavailable) {
      return false;
    }

    try {
      Object key = keyType.getMethod("key", String.class, String.class)
        .invoke(null, "intave", "client-packets");
      Object listener = Proxy.newProxyInstance(
        listenerType.getClassLoader(),
        new Class<?>[]{listenerType},
        new ListenerInvocationHandler()
      );
      listenerHolderType.getMethod("addListener", keyType, listenerType)
        .invoke(null, key, listener);
      this.listenerKey = key;
      return true;
    } catch (ReflectiveOperationException | RuntimeException failure) {
      IntaveLogger.logger().error("Could not use Paper's channel listener; using the fallback injector", reflectionCause(failure));
      return false;
    }
  }

  private void unregisterListener() {
    Object key = this.listenerKey;
    this.listenerKey = null;
    if (key == null) {
      return;
    }
    try {
      Class<?> keyType = Class.forName(ADVENTURE_KEY);
      Class.forName(PAPER_LISTENER_HOLDER).getMethod("removeListener", keyType).invoke(null, key);
    } catch (ReflectiveOperationException | RuntimeException failure) {
      IntaveLogger.logger().error("Could not unregister Intave's Paper channel listener", reflectionCause(failure));
    }
  }

  private static Throwable reflectionCause(Throwable failure) {
    return failure instanceof InvocationTargetException
      ? failure.getCause() : failure;
  }

  private final class ListenerInvocationHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) {
      switch (method.getName()) {
        case "afterInitChannel":
          injectConnectionAfterInitializers((Channel) arguments[0]);
          return null;
        case "toString":
          return "IntaveClientPacketChannelListener";
        case "hashCode":
          return System.identityHashCode(proxy);
        case "equals":
          return proxy == arguments[0];
        default:
          throw new UnsupportedOperationException(method.toString());
      }
    }
  }
}

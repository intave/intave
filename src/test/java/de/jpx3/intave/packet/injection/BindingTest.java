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

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableHandshakePacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.event.PacketSubscription;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.connection.ping.PingWirePacket;
import de.jpx3.intave.packet.wire.window.transaction.TransactionWirePacket;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import de.jpx3.intave.user.User;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

final class BindingTest {
  private static final String RECEIVER = "intave-decoder";
  private static final String SENDER = "intave-encoder";

  @Test
  void matchesDirectAndWrappedChannelsForTheSameConnection() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = Binding.bind(channel)) {
      Channel wrappedChannel = wrapChannel(channel);
      assertNotEquals(wrappedChannel, channel);
      assertTrue(binding.matchesChannel(channel));
      assertTrue(binding.matchesChannel(wrappedChannel));
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void matchesChannelWhenThePipelineAlsoReturnsTheWrapper() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = Binding.bind(channel)) {
      Channel wrappedChannel = wrapChannel(channel, true);
      assertSame(wrappedChannel, wrappedChannel.pipeline().channel());
      assertNotSame(channel.pipeline(), wrappedChannel.pipeline());
      assertTrue(binding.matchesChannel(wrappedChannel));
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void matchesRawChannelWhenBoundThroughAWrappedPipeline() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = Binding.bind(wrapChannel(channel, true))) {
      assertTrue(binding.matchesChannel(channel));
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void rejectsDirectAndWrappedChannelsForAnotherConnection() {
    EmbeddedChannel channel = channelWithViaCodecs();
    EmbeddedChannel otherChannel = channelWithViaCodecs();
    try (Binding binding = Binding.bind(channel); Binding otherBinding = Binding.bind(otherChannel)) {
      assertTrue(otherBinding.matchesChannel(otherChannel));
      assertFalse(binding.matchesChannel(otherChannel));
      assertFalse(binding.matchesChannel(wrapChannel(otherChannel)));
      assertFalse(binding.matchesChannel(wrapChannel(otherChannel, true)));
    } finally {
      channel.finishAndReleaseAll();
      otherChannel.finishAndReleaseAll();
    }
  }

  @Test
  void tracksTheHandshakeConnectionState() {
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = Binding.bind(channel);
    assertEquals(ConnectionState.HANDSHAKING, binding.connectionState());

    ByteBuf handshake = handshake(764);

    assertTrue(channel.writeInbound(handshake));
    assertEquals(ConnectionState.LOGIN, binding.connectionState());
    release(channel.readInbound());

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
  }

  @Test
  void closesUnsupportedProtocolConnections() {
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = Binding.bind(channel);
    ByteBuf handshake = handshake(Integer.MAX_VALUE);

    assertFalse(channel.writeInbound(handshake));
    assertFalse(channel.isOpen());
    assertEquals(0, handshake.refCnt());
    assertEquals(ConnectionState.HANDSHAKING, binding.connectionState());

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
  }

  @Test
  void forwardsDecodedPacketsWhenCompressionIsInsertedBeforeTheDecoder() {
    for (boolean compressed : new boolean[]{false, true}) {
      EmbeddedChannel channel = channelWithViaCodecs();
      try (Binding binding = Binding.bind(channel)) {
        channel.pipeline().addBefore("via-decoder", "decompress", decompressor());
        ByteBuf expected = handshake(764);
        ByteBuf envelope = compressionEnvelope(expected.copy(), compressed);
        try {
          assertTrue(channel.writeInbound(envelope));
          assertEquals(0, envelope.refCnt());
          assertEquals(ConnectionState.LOGIN, binding.connectionState());
          ByteBuf received = channel.readInbound();
          try {
            assertEquals(expected, received);
            assertEquals(1, received.refCnt());
          } finally {
            received.release();
          }
          assertNull(channel.readInbound());
          assertImmediatelyBefore(channel, RECEIVER, "via-decoder");

          ByteBuf nextEnvelope = compressionEnvelope(Unpooled.buffer().writeByte(127), compressed);
          assertTrue(channel.writeInbound(nextEnvelope));
          assertEquals(0, nextEnvelope.refCnt());
          ByteBuf nextPacket = channel.readInbound();
          try {
            assertEquals(127, nextPacket.readUnsignedByte());
            assertFalse(nextPacket.isReadable());
          } finally {
            nextPacket.release();
          }
          assertNull(channel.readInbound());
        } finally {
          expected.release();
        }
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  @Test
  void closesUnsupportedProtocolConnectionsWhileRepairingCompressionPlacement() {
    for (boolean compressed : new boolean[]{false, true}) {
      EmbeddedChannel channel = channelWithViaCodecs();
      try (Binding binding = Binding.bind(channel)) {
        channel.pipeline().addBefore("via-decoder", "decompress", decompressor());
        ByteBuf envelope = compressionEnvelope(handshake(Integer.MAX_VALUE), compressed);

        assertFalse(channel.writeInbound(envelope));
        assertFalse(channel.isOpen());
        assertEquals(0, envelope.refCnt());
        assertEquals(ConnectionState.HANDSHAKING, binding.connectionState());
        assertNull(channel.readInbound());
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  @Test
  void doesNotInspectPacketsRejectedByTheDecompressor() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = Binding.bind(channel)) {
      channel.pipeline().addBefore("via-decoder", "decompress", new MessageToMessageDecoder<ByteBuf>() {
        @Override
        protected void decode(ChannelHandlerContext context, ByteBuf envelope, List<Object> output) {
          throw new DecoderException("Rejected compression envelope");
        }
      });
      ByteBuf envelope = compressionEnvelope(handshake(764), true);

      assertThrows(DecoderException.class, () -> channel.writeInbound(envelope));
      assertEquals(0, envelope.refCnt());
      assertEquals(ConnectionState.HANDSHAKING, binding.connectionState());
      assertNull(channel.readInbound());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void releasesPacketsWhenTheBindingThrows() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndHandshake(channel, 764)) {
      ByteBuf packet = Unpooled.buffer(1);

      assertThrows(IndexOutOfBoundsException.class, () -> channel.writeInbound(packet));
      assertEquals(0, packet.refCnt());
      assertNull(channel.readInbound());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void returnsTheOriginalPacketFromTheSendBinding() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndHandshake(channel, 764)) {
      ByteBuf packet = Unpooled.buffer().writeByte(127);
      try {
        List<ByteBuf> packets = binding.onPacketSend(packet);
        assertEquals(1, packets.size());
        assertSame(packet, packets.get(0));
        assertEquals(1, packet.refCnt());
        assertEquals(0, packet.readerIndex());
      } finally {
        packet.release();
      }
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void releasesOutboundPacketsAndFailsThePromiseWhenTheBindingThrows() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndHandshake(channel, 764)) {
      ByteBuf packet = Unpooled.buffer(1);
      ChannelFuture write = channel.writeAndFlush(packet);

      assertTrue(write.isDone());
      assertInstanceOf(IndexOutOfBoundsException.class, write.cause());
      assertEquals(0, packet.refCnt());
      assertNull(channel.readOutbound());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void releasesAllAllocatedOutboundPacketsWhenEncodingFails() throws ReflectiveOperationException {
    Field field = PacketEventBus.class.getDeclaredField("SERVER_SUBSCRIPTIONS");
    field.setAccessible(true);
    AtomicReference<Map<PacketType.Server, PacketSubscription[]>> subscriptions =
      (AtomicReference<Map<PacketType.Server, PacketSubscription[]>>) field.get(null);
    Map<PacketType.Server, PacketSubscription[]> savedSubscriptions = subscriptions.get();
    try {
      for (int precedingPackets : new int[]{0, 2}) {
        subscriptions.set(new EnumMap<>(PacketType.Server.class));
        PacketEventBus.addListener(event -> {
          PacketSendEvent sendEvent = (PacketSendEvent) event;
          sendEvent.setCancelled(true);
          for (int index = 0; index < precedingPackets; index++) {
            sendEvent.addSendAfter(new TransactionWirePacket().typedTo(PacketType.Server.TRANSACTION_OUT));
          }
          // The final packet has the wrong contract, so its encoder rejects it.
          sendEvent.addSendAfter(new PingWirePacket().typedTo(PacketType.Server.TRANSACTION_OUT));
        }, PacketType.Server.KEEP_ALIVE_OUT);

        EmbeddedChannel channel = channelWithViaCodecs();
        List<ByteBuf> allocated = new ArrayList<>();
        try (Binding binding = bindAndHandshake(channel, 47)) {
          send(channel, packet(PacketIdRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, 47));
          channel.config().setAllocator((ByteBufAllocator) Proxy.newProxyInstance(
            ByteBufAllocator.class.getClassLoader(), new Class<?>[]{ByteBufAllocator.class},
            (proxy, method, arguments) -> {
              Object result = method.invoke(UnpooledByteBufAllocator.DEFAULT, arguments);
              if (result instanceof ByteBuf) {
                allocated.add((ByteBuf) result);
              }
              return result;
            }
          ));
          ByteBuf original = packet(
            PacketIdRegistries.clientbound(), ConnectionState.PLAY, PacketType.Server.KEEP_ALIVE_OUT, 47
          );
          ByteBufStreamCodecs.VARINT.encode(original, 42);

          ChannelFuture write = channel.writeAndFlush(original);

          assertTrue(write.isDone());
          assertInstanceOf(IllegalArgumentException.class, write.cause());
          assertEquals(0, original.refCnt());
          assertEquals(precedingPackets + 1, allocated.size());
          for (ByteBuf buffer : allocated) {
            assertEquals(0, buffer.refCnt(), "Every allocated output must be released on encoding failure");
          }
          assertNull(channel.readOutbound());
        } finally {
          channel.finishAndReleaseAll();
          for (ByteBuf buffer : allocated) {
            if (buffer.refCnt() > 0) {
              buffer.release();
            }
          }
        }
      }
    } finally {
      subscriptions.set(savedSubscriptions);
    }
  }

  @Test
  void recompressesPacketsAfterRepairingTheSenderPlacement() {
    for (boolean compressed : new boolean[]{false, true}) {
      EmbeddedChannel channel = channelWithViaCodecs();
      try (Binding binding = bindAndHandshake(channel, 764)) {
        channel.pipeline().addBefore("via-encoder", "compress", compressor(compressed));
        for (int packetId : new int[]{126, 127}) {
          ByteBuf packet = Unpooled.buffer().writeByte(packetId);
          ChannelFuture write = channel.writeAndFlush(packet);

          assertTrue(write.isSuccess(), () -> String.valueOf(write.cause()));
          assertEquals(0, packet.refCnt());
          ByteBuf envelope = channel.readOutbound();
          try {
            assertEquals(compressed ? 1 : 0, ByteBufStreamCodecs.VARINT.decode(envelope.duplicate()));
            ByteBuf decoded = CompressionRescue.copyPacket(envelope);
            try {
              assertEquals(packetId, decoded.readUnsignedByte());
              assertFalse(decoded.isReadable());
            } finally {
              decoded.release();
            }
          } finally {
            envelope.release();
          }
          assertNull(channel.readOutbound());
          assertImmediatelyBefore(channel, SENDER, "via-encoder");
        }
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  @Test
  void entersPlayAfterLegacyLoginSuccess() {
    int protocolVersion = 763;
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, protocolVersion);

    send(channel, packet(PacketIdRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, protocolVersion));
    assertEquals(ConnectionState.PLAY, binding.connectionState());

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
  }

  @Test
  void followsConfigurationAcknowledgements() {
    int protocolVersion = 764;
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, protocolVersion);

    send(channel, packet(PacketIdRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, protocolVersion));
    assertEquals(ConnectionState.LOGIN, binding.connectionState());

    receive(channel, packet(PacketIdRegistries.serverbound(), ConnectionState.LOGIN, PacketType.Client.LOGIN_ACK, protocolVersion));
    assertEquals(ConnectionState.CONFIGURATION, binding.connectionState());

    receive(channel, packet(
      PacketIdRegistries.serverbound(),
      ConnectionState.CONFIGURATION,
      PacketType.Client.FINISH_CONFIGURATION,
      protocolVersion
    ));
    assertEquals(ConnectionState.PLAY, binding.connectionState());

    receive(channel, packet(
      PacketIdRegistries.serverbound(),
      ConnectionState.PLAY,
      PacketType.Client.CONFIGURATION_ACK,
      protocolVersion
    ));
    assertEquals(ConnectionState.CONFIGURATION, binding.connectionState());

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
  }

  @Test
  void installsOnTheClientSideOfViaVersion() {
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, 764);

    assertImmediatelyBefore(channel, RECEIVER, "via-decoder");
    assertImmediatelyBefore(channel, SENDER, "via-encoder");

    binding.close();
    assertNull(channel.pipeline().context(RECEIVER));
    assertNull(channel.pipeline().context(SENDER));
    assertNotNull(channel.pipeline().context("via-decoder"));
    assertNotNull(channel.pipeline().context("via-encoder"));
    assertFalse(channel.finishAndReleaseAll());
  }

  @Test
  void repairsCodecPlacementAfterCompressionHandlersAreAdded() {
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, 764);

    channel.pipeline().addBefore("via-decoder", "decompress", decompressor());
    channel.pipeline().addBefore("via-encoder", "compress", compressor(false));

    assertTrue(channel.writeInbound(compressionEnvelope(Unpooled.buffer().writeByte(127))));
    assertTrue(channel.writeOutbound(Unpooled.buffer().writeByte(127)));
    release(channel.readInbound());
    release(channel.readOutbound());

    channel.runPendingTasks();
    assertImmediatelyBefore(channel, RECEIVER, "via-decoder");
    assertImmediatelyBefore(channel, SENDER, "via-encoder");

    assertTrue(channel.writeInbound(compressionEnvelope(Unpooled.buffer().writeByte(127))));
    assertTrue(channel.writeOutbound(Unpooled.buffer().writeByte(127)));
    release(channel.readInbound());
    release(channel.readOutbound());

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
  }

  @Test
  void tracksFinishConfigurationWhileViaMovesForCompression() {
    int protocolVersion = 776;
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, protocolVersion);

    send(channel, packet(PacketIdRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, protocolVersion));
    receive(channel, packet(PacketIdRegistries.serverbound(), ConnectionState.LOGIN, PacketType.Client.LOGIN_ACK, protocolVersion));
    assertEquals(ConnectionState.CONFIGURATION, binding.connectionState());

    channel.pipeline().addBefore("via-decoder", "decompress", decompressor());
    receive(channel, compressionEnvelope(packet(
      PacketIdRegistries.serverbound(),
      ConnectionState.CONFIGURATION,
      PacketType.Client.FINISH_CONFIGURATION,
      protocolVersion
    )));

    assertEquals(ConnectionState.PLAY, binding.connectionState());
    channel.runPendingTasks();
    assertImmediatelyBefore(channel, RECEIVER, "via-decoder");

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
  }

  @Test
  void tracksLegacyLoginSuccessWhileViaMovesForCompression() {
    int protocolVersion = 763;
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, protocolVersion);

    channel.pipeline().addBefore("via-encoder", "compress", compressor(false));
    send(channel, packet(
      PacketIdRegistries.clientbound(),
      ConnectionState.LOGIN,
      PacketType.Server.SUCCESS,
      protocolVersion
    ));

    assertEquals(ConnectionState.PLAY, binding.connectionState());
    channel.runPendingTasks();
    assertImmediatelyBefore(channel, SENDER, "via-encoder");

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
  }

  private static Channel wrapChannel(Channel channel) {
    return wrapChannel(channel, false);
  }

  private static Channel wrapChannel(Channel channel, boolean wrapPipeline) {
    return (Channel) Proxy.newProxyInstance(
      Channel.class.getClassLoader(),
      new Class<?>[]{Channel.class},
      (proxy, method, arguments) -> {
        if (method.getName().equals("equals")) {
          return proxy == arguments[0];
        }
        if (wrapPipeline && method.getName().equals("pipeline")) {
          // ProtocolLib 4.8 returns its channel wrapper from PipelineProxy.channel().
          return Proxy.newProxyInstance(
            ChannelPipeline.class.getClassLoader(),
            new Class<?>[]{ChannelPipeline.class},
            (pipelineProxy, pipelineMethod, pipelineArguments) -> pipelineMethod.getName().equals("channel")
              ? proxy
              : pipelineMethod.invoke(channel.pipeline(), pipelineArguments)
          );
        }
        return method.invoke(channel, arguments);
      }
    );
  }

  private static EmbeddedChannel channelWithViaCodecs() {
    EmbeddedChannel channel = new EmbeddedChannel();
    channel.pipeline().addLast("via-decoder", new ChannelInboundHandlerAdapter());
    channel.pipeline().addLast("via-encoder", new ChannelOutboundHandlerAdapter());
    return channel;
  }

  private static Binding bindAndHandshake(EmbeddedChannel channel, int protocolVersion) {
    Binding binding = Binding.bind(channel);
    receive(channel, handshake(protocolVersion));
    // Send events need a user lookup; cache a test user without requiring a running Bukkit server.
    try {
      Field user = Binding.class.getDeclaredField("user");
      user.setAccessible(true);
      user.set(binding, Proxy.newProxyInstance(
        User.class.getClassLoader(), new Class<?>[]{User.class}, (proxy, method, arguments) -> null
      ));
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
    return binding;
  }

  private static ByteBuf handshake(int protocolVersion) {
    // Use a supported registry version even when testing an unknown advertised protocol.
    MutableHandshakePacket packet = WirePackets.createPacket(ConnectionState.HANDSHAKING, PacketType.Client.SET_PROTOCOL, 764);
    packet.setProtocolVersion(protocolVersion);
    packet.setServerAddress("localhost");
    packet.setServerPort(25565);
    packet.setNextConnectionState(ConnectionState.LOGIN);
    ByteBuf handshake = Unpooled.buffer();
    WirePackets.encodePacket(PacketDirection.SERVERBOUND, ConnectionState.HANDSHAKING, PacketType.Client.SET_PROTOCOL, 764, packet, handshake);
    return handshake;
  }

  private static ByteBuf packet(
    PacketIdRegistry<?> registry,
    ConnectionState state,
    PacketType typeName,
    int protocolVersion
  ) {
    ByteBuf packet = Unpooled.buffer();
    ByteBufStreamCodecs.VARINT.encode(packet, registry.idOf(state, typeName, protocolVersion));
    return packet;
  }

  private static ByteBuf compressionEnvelope(ByteBuf packet) {
    return compressionEnvelope(packet, false);
  }

  private static ByteBuf compressionEnvelope(ByteBuf packet, boolean compressed) {
    ByteBuf envelope = Unpooled.buffer();
    try {
      ByteBufStreamCodecs.VARINT.encode(envelope, compressed ? packet.readableBytes() : 0);
      if (!compressed) {
        envelope.writeBytes(packet);
      } else {
        Deflater deflater = new Deflater();
        try {
          deflater.setInput(ByteBufUtil.getBytes(packet));
          deflater.finish();
          byte[] bytes = new byte[256];
          while (!deflater.finished()) {
            envelope.writeBytes(bytes, 0, deflater.deflate(bytes));
          }
        } finally {
          deflater.end();
        }
      }
      return envelope;
    } finally {
      assertTrue(packet.release());
    }
  }

  private static MessageToMessageDecoder<ByteBuf> decompressor() {
    return new MessageToMessageDecoder<ByteBuf>() {
      @Override
      protected void decode(ChannelHandlerContext context, ByteBuf envelope, List<Object> output) {
        output.add(CompressionRescue.copyPacket(envelope));
      }
    };
  }

  private static MessageToMessageEncoder<ByteBuf> compressor(boolean compressed) {
    return new MessageToMessageEncoder<ByteBuf>() {
      @Override
      protected void encode(ChannelHandlerContext context, ByteBuf packet, List<Object> output) {
        output.add(compressionEnvelope(packet.copy(), compressed));
      }
    };
  }

  private static void receive(EmbeddedChannel channel, ByteBuf packet) {
    assertTrue(channel.writeInbound(packet));
    release(channel.readInbound());
  }

  private static void send(EmbeddedChannel channel, ByteBuf packet) {
    assertTrue(channel.writeOutbound(packet));
    release(channel.readOutbound());
  }

  private static void assertImmediatelyBefore(EmbeddedChannel channel, String handler, String anchor) {
    List<String> names = channel.pipeline().names();
    assertEquals(names.indexOf(handler) + 1, names.indexOf(anchor));
  }

  private static void release(ByteBuf buffer) {
    assertTrue(buffer.release());
  }
}

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
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.packet.contract.immutable.PingPacket;
import de.jpx3.intave.packet.contract.mutable.MutableEntityVelocityPacket;
import de.jpx3.intave.packet.contract.mutable.MutableHandshakePacket;
import de.jpx3.intave.packet.contract.mutable.MutableHeldItemSlotPacket;
import de.jpx3.intave.packet.event.PacketEventBus;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.packet.event.PacketSubscription;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.packet.wire.connection.bundle.BundleDelimiterWirePacket;
import de.jpx3.intave.packet.wire.connection.ping.PingWirePacket;
import de.jpx3.intave.packet.wire.entity.velocity.EntityVelocityWirePacket;
import de.jpx3.intave.packet.wire.item.heldslot.HeldItemSlotWirePacket;
import de.jpx3.intave.packet.wire.window.transaction.TransactionWirePacket;
import de.jpx3.intave.packet.wire.world.explosion.ExplosionOptionalMotionWirePacket;
import de.jpx3.intave.registry.PacketTypeRegistries;
import de.jpx3.intave.registry.PacketTypeRegistry;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.*;

final class BindingTest {
  private static final String RECEIVER = "intave-decoder";
  private static final String SENDER = "intave-encoder";
  private static final TypedPacketContract DELIMITER = new BundleDelimiterWirePacket().typedTo(PacketType.Server.BUNDLE_DELIMITER);
  private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
  private PrintStream originalError;
  private PrintStream capturedError;
  private AtomicReference<Map<PacketType.Client, PacketSubscription[]>> clientSubscriptions;
  private Map<PacketType.Client, PacketSubscription[]> savedClientSubscriptions;

  @Test
  void bundlesQueuedPacketsAndOmitsEmptyGroupsAfterCancellation() {
    PacketSubscription listener = event -> {
      PacketSendEvent send = (PacketSendEvent) event;
      int id = ((PingPacket) send.packet()).id();
      send.requireInBundle();
      send.requireInBundle();
      if (id == 1 || id == 2) {
        send.addSendBefore(ping(11));
        send.addSendAfter(ping(12));
      }
      send.setCancelled(id == 2 || id == 3);
    };
    PacketSubscription unexpectedDelimiter = event -> fail("Generated delimiters must bypass subscriptions");
    PacketEventBus.addListener(listener, PacketType.Server.PING);
    PacketEventBus.addListener(unexpectedDelimiter, PacketType.Server.BUNDLE_DELIMITER);
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndPlay(channel, 762)) {
      assertTrue(binding.send(ping(1)).isSuccess());
      assertBundleOutput(channel, binding, DELIMITER, ping(11), ping(1), ping(12), DELIMITER);
      assertTrue(binding.send(ping(2)).isSuccess());
      assertBundleOutput(channel, binding, DELIMITER, ping(11), ping(12), DELIMITER);
      assertTrue(binding.send(ping(3)).isSuccess());
      assertNull(channel.readOutbound());
    } finally {
      PacketEventBus.removeListener(listener);
      PacketEventBus.removeListener(unexpectedDelimiter);
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void reusesAnOpenBundleAcrossWritesOnTheSameConnectionOnly() {
    PacketSubscription listener = event -> ((PacketSendEvent) event).requireInBundle();
    PacketEventBus.addListener(listener, PacketType.Server.PING);
    EmbeddedChannel first = channelWithViaCodecs();
    EmbeddedChannel second = channelWithViaCodecs();
    try (Binding binding = bindAndPlay(first, 773); Binding other = bindAndPlay(second, 773)) {
      ByteBuf serverDelimiter = packet(PacketTypeRegistries.clientbound(), ConnectionState.PLAY,
        PacketType.Server.BUNDLE_DELIMITER, 773);
      assertTrue(first.writeOutbound(serverDelimiter));
      assertTrue(binding.send(ping(1)).isSuccess());
      assertTrue(other.send(ping(2)).isSuccess());
      assertBundleOutput(second, other, DELIMITER, ping(2), DELIMITER);
      assertTrue(binding.sendSilently(DELIMITER).isSuccess());
      assertTrue(binding.send(ping(3)).isSuccess());
      assertBundleOutput(first, binding, DELIMITER, ping(1), DELIMITER, DELIMITER, ping(3), DELIMITER);
    } finally {
      PacketEventBus.removeListener(listener);
      first.finishAndReleaseAll();
      second.finishAndReleaseAll();
    }
  }

  @Test
  void observesServerDelimiterForwardedWhileRepairingSenderPlacement() {
    PacketSubscription listener = event -> ((PacketSendEvent) event).requireInBundle();
    PacketEventBus.addListener(listener, PacketType.Server.PING);
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndPlay(channel, 773)) {
      channel.pipeline().addBefore("via-encoder", "passthrough", new ChannelOutboundHandlerAdapter());
      assertTrue(channel.writeOutbound(packet(PacketTypeRegistries.clientbound(), ConnectionState.PLAY,
        PacketType.Server.BUNDLE_DELIMITER, 773)));
      channel.runPendingTasks();
      assertTrue(binding.send(ping(1)).isSuccess());
      assertTrue(binding.sendSilently(DELIMITER).isSuccess());
      assertBundleOutput(channel, binding, DELIMITER, ping(1), DELIMITER);
    } finally {
      PacketEventBus.removeListener(listener);
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void ignoresBundleRequestsOnLegacyClientsAndOutsidePlay() {
    PacketSubscription listener = event -> ((PacketSendEvent) event).requireInBundle();
    PacketEventBus.addListener(listener, PacketType.Server.PING);
    EmbeddedChannel legacy = channelWithViaCodecs();
    EmbeddedChannel modern = channelWithViaCodecs();
    try (Binding binding = bindAndPlay(legacy, 761); Binding configuring = bindAndHandshake(modern, 773)) {
      assertTrue(binding.send(ping(1)).isSuccess());
      assertBundleOutput(legacy, binding, ping(1));

      receive(modern, packet(PacketTypeRegistries.serverbound(), ConnectionState.LOGIN, PacketType.Client.LOGIN_ACK, 773));
      assertEquals(ConnectionState.CONFIGURATION, configuring.connectionState());
      assertTrue(modern.writeOutbound(packet(PacketTypeRegistries.clientbound(), ConnectionState.CONFIGURATION,
        PacketType.Server.PING, 773).writeInt(2)));
      assertBundleOutput(modern, configuring, ping(2));
    } finally {
      PacketEventBus.removeListener(listener);
      legacy.finishAndReleaseAll();
      modern.finishAndReleaseAll();
    }
  }

  @Test
  void encodingFailureWritesNoPartOfTheBundleAndTheNextSendCanBundle() {
    PacketSubscription listener = event -> {
      PacketSendEvent send = (PacketSendEvent) event;
      send.requireInBundle();
      if (((PingPacket) send.packet()).id() == 1) {
        send.addSendAfter(new PingWirePacket().typedTo(PacketType.Server.ENTITY_VELOCITY));
      }
    };
    PacketEventBus.addListener(listener, PacketType.Server.PING);
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndPlay(channel, 773)) {
      ByteBuf original = packet(PacketTypeRegistries.clientbound(), ConnectionState.PLAY, PacketType.Server.PING, 773).writeInt(1);
      assertInstanceOf(IllegalArgumentException.class, channel.writeAndFlush(original).cause());
      assertEquals(0, original.refCnt());
      assertNull(channel.readOutbound());
      assertTrue(binding.send(ping(2)).isSuccess());
      assertBundleOutput(channel, binding, DELIMITER, ping(2), DELIMITER);
    } finally {
      PacketEventBus.removeListener(listener);
      channel.finishAndReleaseAll();
    }
  }

  private static TypedPacketContract ping(int id) {
    return new PingWirePacket(id).typedTo(PacketType.Server.PING);
  }

  private static void assertBundleOutput(EmbeddedChannel channel, Binding binding, TypedPacketContract... expected) {
    for (TypedPacketContract packet : expected) {
      ByteBuf bytes = channel.readOutbound();
      assertNotNull(bytes, () -> "Missing " + packet);
      try {
        assertEquals(PacketTypeRegistries.clientbound().idOf(binding.connectionState(), packet.type(), binding.protocolVersion()),
          ByteBufStreamCodecs.VARINT.decode(bytes));
        if (packet.type() == PacketType.Server.PING) {
          assertEquals(((PingPacket) packet.contract()).id(), bytes.readInt());
        }
        assertFalse(bytes.isReadable());
      } finally {
        bytes.release();
      }
    }
    assertNull(channel.readOutbound());
  }

  @Test
  void explicitSubscriptionBypassStillCompressesAndWritesThePacket() {
    PacketSubscription subscription = event -> fail("Already tracked teleport motion must not be tracked again");
    PacketEventBus.addListener(subscription, PacketType.Server.ENTITY_VELOCITY);
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndPlay(channel, 773)) {
      channel.pipeline().addBefore("via-encoder", "compress", compressor(true));
      ChannelFuture sent = binding.sendSilently(new EntityVelocityWirePacket(1, new Motion(0.125, 0.25, 0.5))
        .typedTo(PacketType.Server.ENTITY_VELOCITY));
      assertTrue(sent.isSuccess(), () -> String.valueOf(sent.cause()));
      ByteBuf envelope = channel.readOutbound();
      ByteBuf packet = CompressionRescue.copyPacket(envelope);
      try {
        MutableEntityVelocityPacket decoded = WirePackets.decodePacket(PacketDirection.CLIENTBOUND, ConnectionState.PLAY, 773, packet);
        assertEquals(1, decoded.entityId());
        assertEquals(0.25, decoded.motion().motionY(), 0.001);
      } finally {
        packet.release();
        envelope.release();
      }
      assertNull(channel.readOutbound());
    } finally {
      PacketEventBus.removeListener(subscription);
      channel.finishAndReleaseAll();
    }
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void isolateClientSubscriptions() throws ReflectiveOperationException {
    Field field = PacketEventBus.class.getDeclaredField("CLIENT_SUBSCRIPTIONS");
    field.setAccessible(true);
    clientSubscriptions = (AtomicReference<Map<PacketType.Client, PacketSubscription[]>>) field.get(null);
    savedClientSubscriptions = clientSubscriptions.getAndSet(new EnumMap<>(PacketType.Client.class));
  }

  @AfterEach
  void restoreClientSubscriptions() {
    clientSubscriptions.set(savedClientSubscriptions);
  }

  @BeforeEach
  void captureErrors() {
    originalError = System.err;
    capturedError = new PrintStream(errors, true, StandardCharsets.UTF_8);
    System.setErr(capturedError);
  }

  @AfterEach
  void restoreErrors() {
    System.setErr(originalError);
    capturedError.close();
  }

  @Test
  void nativeSendsBypassServerEncodersAndRunSubscriptionsOnce() {
    for (boolean compressed : new boolean[]{false, true}) {
      AtomicInteger calls = new AtomicInteger();
      PacketSubscription subscription = event -> {
        calls.incrementAndGet();
        ((MutableEntityVelocityPacket) event.packet()).setEntityId(456);
      };
      PacketEventBus.addListener(subscription, PacketType.Server.ENTITY_VELOCITY);
      EmbeddedChannel channel = channelWithViaCodecs();
      try (Binding binding = bindAndPlay(channel, 773)) {
        assertSame(binding, Binding.of(wrapChannel(channel, true)));
        channel.pipeline().replace("via-encoder", "via-encoder", new ChannelOutboundHandlerAdapter() {
          @Override
          public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            fail("Native client-version bytes must not enter ViaVersion's encoder");
          }
        });
        channel.pipeline().addBefore("via-encoder", "compress", compressor(compressed));
        ChannelFuture sent = binding.send(new EntityVelocityWirePacket(123, new Motion(8, 0.4, -8))
          .typedTo(PacketType.Server.ENTITY_VELOCITY));
        assertTrue(sent.isSuccess(), () -> String.valueOf(sent.cause()));
        assertEquals(1, calls.get());
        ByteBuf envelope = channel.readOutbound();
        ByteBuf packet = CompressionRescue.copyPacket(envelope);
        try {
          MutableEntityVelocityPacket decoded = WirePackets.decodePacket(PacketDirection.CLIENTBOUND, ConnectionState.PLAY, 773, packet);
          assertEquals(456, decoded.entityId());
          assertEquals(8, decoded.motion().motionX(), 0.001);
          assertEquals(-8, decoded.motion().motionZ(), 0.001);
        } finally {
          packet.release();
          envelope.release();
        }
        assertNull(channel.readOutbound());
      } finally {
        PacketEventBus.removeListener(subscription);
        channel.finishAndReleaseAll();
      }
    }
  }

  @Test
  void nativeCancellationKeepsQueuedFeedbackInOrder() {
    for (boolean cancel : new boolean[]{false, true}) {
      AtomicInteger feedbackCalls = new AtomicInteger();
      PacketSubscription motion = event -> {
        PacketSendEvent send = (PacketSendEvent) event;
        send.setCancelled(cancel);
        send.addSendBefore(new PingWirePacket(11).typedTo(PacketType.Server.PING));
        send.addSendAfter(new PingWirePacket(22).typedTo(PacketType.Server.PING));
      };
      PacketSubscription feedback = event -> feedbackCalls.incrementAndGet();
      PacketEventBus.addListener(motion, PacketType.Server.EXPLOSION);
      PacketEventBus.addListener(feedback, PacketType.Server.PING);
      EmbeddedChannel channel = channelWithViaCodecs();
      try (Binding binding = bindAndPlay(channel, 777)) {
        ExplosionOptionalMotionWirePacket explosion = WirePackets.createPlayPacket(PacketType.Server.EXPLOSION, 777);
        explosion.setDefaultEffects(777);
        ChannelFuture sent = binding.send(explosion.typedTo(PacketType.Server.EXPLOSION));
        assertTrue(sent.isSuccess(), () -> String.valueOf(sent.cause()));
        assertEquals(2, feedbackCalls.get());
        for (PacketType.Server type : cancel ? List.of(PacketType.Server.PING, PacketType.Server.PING)
          : List.of(PacketType.Server.PING, PacketType.Server.EXPLOSION, PacketType.Server.PING)) {
          ByteBuf packet = channel.readOutbound();
          assertNotNull(packet);
          try {
            assertEquals(PacketTypeRegistries.clientbound().idOf(ConnectionState.PLAY, type, 777), ByteBufStreamCodecs.VARINT.decode(packet));
            if (type == PacketType.Server.PING) assertEquals(channel.outboundMessages().isEmpty() ? 22 : 11, packet.readInt());
          } finally {
            packet.release();
          }
        }
        assertNull(channel.readOutbound());
      } finally {
        PacketEventBus.removeListener(motion);
        PacketEventBus.removeListener(feedback);
        channel.finishAndReleaseAll();
      }
    }
  }

  @Test
  void nativeSendRejectsWrongStateDirectionVersionAndClosedBindings() {
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, 47);
    try {
      assertInstanceOf(IllegalArgumentException.class, binding.send(new PingWirePacket(1).typedTo(PacketType.Server.PING)).cause());
      send(channel, packet(PacketTypeRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, 47));
      assertInstanceOf(IllegalArgumentException.class, binding.send(new HeldItemSlotWirePacket(1).typedTo(PacketType.Client.HELD_ITEM_SLOT_IN)).cause());
      assertFalse(binding.send(new EntityVelocityWirePacket(1, new Motion()).typedTo(PacketType.Server.ENTITY_VELOCITY)).isSuccess());
      binding.close();
      assertThrows(IllegalStateException.class, () -> Binding.of(channel));
      assertInstanceOf(IllegalStateException.class, binding.send(new PingWirePacket(1).typedTo(PacketType.Server.PING)).cause());
      assertNull(channel.readOutbound());
    } finally {
      binding.close();
      channel.finishAndReleaseAll();
    }
  }

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

      Throwable failure = assertThrows(IndexOutOfBoundsException.class, () -> channel.writeInbound(packet));
      assertLogged("processing SERVERBOUND", failure, ConnectionState.LOGIN, 764);
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
  void forwardsUnchangedIncomingPacketsWithoutCopying() {
    PacketEventBus.addListener(event ->
      assertEquals(3, ((MutableHeldItemSlotPacket) event.packet()).slot()), PacketType.Client.HELD_ITEM_SLOT_IN);
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndPlay(channel, 47)) {
      ByteBuf original = heldSlot(47, 3);
      int readerIndex = original.readerIndex();
      int writerIndex = original.writerIndex();

      assertTrue(channel.writeInbound(original));
      ByteBuf forwarded = channel.readInbound();
      try {
        assertSame(original, forwarded);
        assertEquals(readerIndex, forwarded.readerIndex());
        assertEquals(writerIndex, forwarded.writerIndex());
        assertEquals(1, forwarded.refCnt());
      } finally {
        forwarded.release();
      }
      assertNull(channel.readInbound());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void cancelsIncomingPacketsWithoutClosingTheConnection() {
    PacketEventBus.addListener(event -> {
      if (((MutableHeldItemSlotPacket) event.packet()).slot() == 3) {
        // Cancellation must win even over a replacement that cannot be encoded.
        event.setPacket(new PingWirePacket());
        event.setCancelled(true);
      }
    }, PacketType.Client.HELD_ITEM_SLOT_IN);
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndPlay(channel, 47)) {
      ByteBuf cancelled = heldSlot(47, 3);
      assertFalse(channel.writeInbound(cancelled));
      assertEquals(0, cancelled.refCnt());
      assertNull(channel.readInbound());
      assertTrue(channel.isOpen());
      assertEquals(ConnectionState.PLAY, binding.connectionState());

      ByteBuf next = heldSlot(47, 4);
      assertTrue(channel.writeInbound(next));
      ByteBuf forwarded = channel.readInbound();
      try {
        assertSame(next, forwarded);
        assertEquals(1, forwarded.refCnt());
      } finally {
        forwarded.release();
      }
      assertEquals("", errors.toString(StandardCharsets.UTF_8));
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void forwardsIncomingPacketMutationsAndReplacements() {
    for (boolean replace : new boolean[]{false, true}) {
      clientSubscriptions.set(new EnumMap<>(PacketType.Client.class));
      PacketEventBus.addListener(event -> {
        if (replace) {
          // A new packet need not have its mutable flag set.
          event.setPacket(new HeldItemSlotWirePacket(7));
        } else {
          ((MutableHeldItemSlotPacket) event.packet()).setSlot(7);
        }
      }, PacketType.Client.HELD_ITEM_SLOT_IN);
      for (int protocolVersion : new int[]{47, 776}) {
        EmbeddedChannel channel = channelWithViaCodecs();
        try (Binding binding = bindAndPlay(channel, protocolVersion)) {
          ByteBuf original = heldSlot(protocolVersion, 3);
          assertTrue(channel.writeInbound(original));
          assertEquals(0, original.refCnt());
          assertHeldSlot(channel, protocolVersion, 7);
          assertTrue(channel.isOpen());
          assertEquals(ConnectionState.PLAY, binding.connectionState());
        } finally {
          channel.finishAndReleaseAll();
        }
      }
    }
  }

  @Test
  void appliesIncomingCancellationAndEditsAfterRepairingCompressionPlacement() {
    PacketEventBus.addListener(event -> {
      MutableHeldItemSlotPacket packet = (MutableHeldItemSlotPacket) event.packet();
      if (packet.slot() == 3) {
        event.setCancelled(true);
      } else {
        packet.setSlot(7);
      }
    }, PacketType.Client.HELD_ITEM_SLOT_IN);
    for (boolean compressed : new boolean[]{false, true}) {
      EmbeddedChannel channel = channelWithViaCodecs();
      try (Binding binding = bindAndPlay(channel, 47)) {
        channel.pipeline().addBefore("via-decoder", "decompress", decompressor());
        ByteBuf cancelled = compressionEnvelope(heldSlot(47, 3), compressed);
        assertFalse(channel.writeInbound(cancelled));
        assertEquals(0, cancelled.refCnt());
        assertTrue(channel.isOpen());
        assertNull(channel.readInbound());
        assertImmediatelyBefore(channel, RECEIVER, "via-decoder");

        ByteBuf edited = compressionEnvelope(heldSlot(47, 4), compressed);
        assertTrue(channel.writeInbound(edited));
        assertEquals(0, edited.refCnt());
        assertHeldSlot(channel, 47, 7);
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  @Test
  void releasesIncomingPacketsWhenASubscriberThrows() {
    AssertionError failure = new AssertionError("incoming subscriber failed");
    PacketEventBus.addListener(event -> { throw failure; }, PacketType.Client.HELD_ITEM_SLOT_IN);
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndPlay(channel, 47)) {
      ByteBuf original = heldSlot(47, 3);
      assertSame(failure, assertThrows(AssertionError.class, () -> channel.writeInbound(original)));
      assertEquals(0, original.refCnt());
      assertNull(channel.readInbound());
      assertLogged("processing SERVERBOUND", failure, ConnectionState.PLAY, 47);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void releasesIncomingAndAllocatedPacketsWhenEncodingFails() {
    PacketEventBus.addListener(event -> event.setPacket(new PingWirePacket()), PacketType.Client.HELD_ITEM_SLOT_IN);
    EmbeddedChannel channel = channelWithViaCodecs();
    List<ByteBuf> allocated = new ArrayList<>();
    try (Binding binding = bindAndPlay(channel, 47)) {
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
      ByteBuf original = heldSlot(47, 3);

      Throwable failure = assertThrows(IllegalArgumentException.class, () -> channel.writeInbound(original));

      assertEquals(0, original.refCnt());
      assertEquals(1, allocated.size());
      assertEquals(0, allocated.get(0).refCnt());
      assertNull(channel.readInbound());
      assertEquals(ConnectionState.PLAY, binding.connectionState());
      assertLogged("processing SERVERBOUND", failure, ConnectionState.PLAY, 47);
    } finally {
      channel.finishAndReleaseAll();
      for (ByteBuf buffer : allocated) {
        if (buffer.refCnt() > 0) {
          buffer.release();
        }
      }
    }
  }

  @Test
  void forwardsEmptyOutboundBuffersWithoutReadingAPacketHeader() {
    for (boolean consumed : new boolean[]{false, true}) {
      EmbeddedChannel channel = channelWithViaCodecs();
      try (Binding binding = bindAndPlay(channel, 774)) {
        ByteBuf original = consumed
          ? Unpooled.buffer(1).writeByte(0).readerIndex(1)
          : channel.alloc().directBuffer(0, 1);
        ChannelFuture write = channel.writeAndFlush(original);
        ByteBuf forwarded = channel.readOutbound();
        try {
          assertTrue(write.isSuccess(), () -> String.valueOf(write.cause()));
          assertSame(original, forwarded);
          assertEquals(1, original.refCnt());
          assertEquals(consumed ? 1 : 0, original.readerIndex());
          assertFalse(forwarded.isReadable());
          assertEquals(ConnectionState.PLAY, binding.connectionState());
          assertTrue(channel.isActive());
          assertEquals("", errors.toString(StandardCharsets.UTF_8));
        } finally {
          if (forwarded != null) forwarded.release();
        }
        assertEquals(0, original.refCnt());
        assertNull(channel.readOutbound());
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  @Test
  void preservesEmptyEncoderWritesWhenCompressionPlacementChanges() {
    for (boolean moved : new boolean[]{false, true}) {
      EmbeddedChannel channel = channelWithViaCodecs();
      try (Binding binding = bindAndPlay(channel, 774)) {
        channel.pipeline().replace("via-encoder", "via-encoder", new MessageToByteEncoder<Integer>() {
          @Override
          protected void encode(ChannelHandlerContext context, Integer message, ByteBuf output) {
            // A cancelled packet produces no bytes, so Netty forwards an empty buffer.
          }
        });
        if (moved) {
          channel.pipeline().addBefore("via-encoder", "compress", new ChannelOutboundHandlerAdapter());
        }

        ChannelFuture write = channel.writeAndFlush(1);
        ByteBuf forwarded = channel.readOutbound();
        try {
          assertTrue(write.isSuccess(), () -> String.valueOf(write.cause()));
          assertNotNull(forwarded);
          assertFalse(forwarded.isReadable());
          assertEquals(ConnectionState.PLAY, binding.connectionState());
          assertTrue(channel.isActive());
          assertEquals("", errors.toString(StandardCharsets.UTF_8));
        } finally {
          if (forwarded != null) forwarded.release();
        }
        assertNull(channel.readOutbound());
      } finally {
        channel.finishAndReleaseAll();
      }
    }
  }

  @Test
  void releasesOutboundPacketsAndFailsThePromiseWhenTheBindingThrows() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndHandshake(channel, 764)) {
      ByteBuf packet = Unpooled.buffer(1).writeByte(0x80);
      ChannelFuture write = channel.writeAndFlush(packet);

      assertTrue(write.isDone());
      assertInstanceOf(IndexOutOfBoundsException.class, write.cause());
      assertLogged("processing CLIENTBOUND", write.cause(), ConnectionState.LOGIN, 764);
      assertEquals(0, packet.refCnt());
      assertNull(channel.readOutbound());
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void logsMalformedHandshakesBeforeClosingTheConnection() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = Binding.bind(channel)) {
      ByteBuf packet = Unpooled.buffer().writeByte(0);

      assertFalse(channel.writeInbound(packet));
      assertFalse(channel.isOpen());
      assertEquals(0, packet.refCnt());
      String output = errors.toString(StandardCharsets.UTF_8);
      assertTrue(output.contains("decoding handshake SERVERBOUND"), output);
      assertTrue(output.contains("state=HANDSHAKING, protocol=-1"), output);
      assertTrue(output.contains("IndexOutOfBoundsException"), output);
      assertTrue(output.contains("\tat " + Binding.class.getName()), output);
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  void decodesOutgoingPacketsUsingTheClientboundRegistry() {
    EmbeddedChannel channel = channelWithViaCodecs();
    try (Binding binding = bindAndHandshake(channel, 47)) {
      send(channel, packet(PacketTypeRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, 47));
      ByteBuf original = Unpooled.buffer();
      WirePackets.encodePacket(PacketDirection.CLIENTBOUND, ConnectionState.PLAY, PacketType.Server.TRANSACTION_OUT,
        47, new TransactionWirePacket(3, (short) 7, true), original);

      ChannelFuture write = channel.writeAndFlush(original);

      assertTrue(write.isSuccess(), () -> String.valueOf(write.cause()));
      assertSame(original, channel.readOutbound());
      original.release();
      assertEquals("", errors.toString(StandardCharsets.UTF_8));
    } finally {
      channel.finishAndReleaseAll();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void logsSubscriberErrorsAndPreservesTheOriginalFailure() throws ReflectiveOperationException {
    Field field = PacketEventBus.class.getDeclaredField("SERVER_SUBSCRIPTIONS");
    field.setAccessible(true);
    AtomicReference<Map<PacketType.Server, PacketSubscription[]>> subscriptions =
      (AtomicReference<Map<PacketType.Server, PacketSubscription[]>>) field.get(null);
    Map<PacketType.Server, PacketSubscription[]> saved = subscriptions.getAndSet(new EnumMap<>(PacketType.Server.class));
    AssertionError failure = new AssertionError("subscriber failed", new IllegalStateException("original cause"));
    try {
      PacketEventBus.addListener(event -> { throw failure; }, PacketType.Server.TRANSACTION_OUT);
      EmbeddedChannel channel = channelWithViaCodecs();
      try (Binding binding = bindAndHandshake(channel, 47)) {
        send(channel, packet(PacketTypeRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, 47));
        ByteBuf original = Unpooled.buffer();
        WirePackets.encodePacket(PacketDirection.CLIENTBOUND, ConnectionState.PLAY, PacketType.Server.TRANSACTION_OUT,
          47, new TransactionWirePacket(), original);

        ChannelFuture write = channel.writeAndFlush(original);

        assertSame(failure, write.cause());
        assertEquals(0, original.refCnt());
        assertLogged("processing CLIENTBOUND", failure, ConnectionState.PLAY, 47);
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Caused by: java.lang.IllegalStateException: original cause"));
      } finally {
        channel.finishAndReleaseAll();
      }
    } finally {
      subscriptions.set(saved);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void outgoingCancellationPreservesQueuedPackets() throws ReflectiveOperationException {
    Field field = PacketEventBus.class.getDeclaredField("SERVER_SUBSCRIPTIONS");
    field.setAccessible(true);
    AtomicReference<Map<PacketType.Server, PacketSubscription[]>> subscriptions =
      (AtomicReference<Map<PacketType.Server, PacketSubscription[]>>) field.get(null);
    Map<PacketType.Server, PacketSubscription[]> savedSubscriptions = subscriptions.get();
    try {
      for (int queuedPackets : new int[]{0, 1, 2}) {
        subscriptions.set(new EnumMap<>(PacketType.Server.class));
        PacketEventBus.addListener(event -> {
          PacketSendEvent sendEvent = (PacketSendEvent) event;
          sendEvent.setCancelled(true);
          if (queuedPackets >= 1) {
            sendEvent.addSendBefore(new TransactionWirePacket(3, (short) 1, true).typedTo(PacketType.Server.TRANSACTION_OUT));
          }
          if (queuedPackets >= 2) {
            sendEvent.addSendAfter(new TransactionWirePacket(3, (short) 2, true).typedTo(PacketType.Server.TRANSACTION_OUT));
          }
        }, PacketType.Server.KEEP_ALIVE_OUT);

        EmbeddedChannel channel = channelWithViaCodecs();
        try (Binding binding = bindAndPlay(channel, 47)) {
          ByteBuf original = packet(PacketTypeRegistries.clientbound(), ConnectionState.PLAY, PacketType.Server.KEEP_ALIVE_OUT, 47);
          ByteBufStreamCodecs.VARINT.encode(original, 42);

          ChannelFuture write = channel.writeAndFlush(original);

          assertTrue(write.isSuccess(), () -> String.valueOf(write.cause()));
          assertEquals(0, original.refCnt());
          assertTrue(channel.isOpen());
          for (int index = 0; index < queuedPackets; index++) {
            ByteBuf forwarded = channel.readOutbound();
            assertNotNull(forwarded, "Queued packet " + index + " must survive parent cancellation");
            try {
              assertEquals(1, forwarded.refCnt());
              assertEquals(PacketTypeRegistries.clientbound().idOf(ConnectionState.PLAY, PacketType.Server.TRANSACTION_OUT, 47),
                ByteBufStreamCodecs.VARINT.decode(forwarded));
              assertEquals(3, forwarded.readUnsignedByte());
              assertEquals(index + 1, forwarded.readShort());
              assertTrue(forwarded.readBoolean());
              assertFalse(forwarded.isReadable());
            } finally {
              forwarded.release();
            }
          }
          assertNull(channel.readOutbound());
          assertEquals("", errors.toString(StandardCharsets.UTF_8));
        } finally {
          channel.finishAndReleaseAll();
        }
      }
    } finally {
      subscriptions.set(savedSubscriptions);
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
        errors.reset();
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
          send(channel, packet(PacketTypeRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, 47));
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
            PacketTypeRegistries.clientbound(), ConnectionState.PLAY, PacketType.Server.KEEP_ALIVE_OUT, 47
          );
          ByteBufStreamCodecs.VARINT.encode(original, 42);

          ChannelFuture write = channel.writeAndFlush(original);

          assertTrue(write.isDone());
          assertInstanceOf(IllegalArgumentException.class, write.cause());
          assertLogged("processing CLIENTBOUND", write.cause(), ConnectionState.PLAY, 47);
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

    send(channel, packet(PacketTypeRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, protocolVersion));
    assertEquals(ConnectionState.PLAY, binding.connectionState());

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
  }

  @Test
  void followsConfigurationAcknowledgements() {
    int protocolVersion = 764;
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, protocolVersion);

    send(channel, packet(PacketTypeRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, protocolVersion));
    assertEquals(ConnectionState.LOGIN, binding.connectionState());

    receive(channel, packet(PacketTypeRegistries.serverbound(), ConnectionState.LOGIN, PacketType.Client.LOGIN_ACK, protocolVersion));
    assertEquals(ConnectionState.CONFIGURATION, binding.connectionState());

    receive(channel, packet(
      PacketTypeRegistries.serverbound(),
      ConnectionState.CONFIGURATION,
      PacketType.Client.FINISH_CONFIGURATION,
      protocolVersion
    ));
    assertEquals(ConnectionState.PLAY, binding.connectionState());

    receive(channel, packet(
      PacketTypeRegistries.serverbound(),
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

    send(channel, packet(PacketTypeRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, protocolVersion));
    receive(channel, packet(PacketTypeRegistries.serverbound(), ConnectionState.LOGIN, PacketType.Client.LOGIN_ACK, protocolVersion));
    assertEquals(ConnectionState.CONFIGURATION, binding.connectionState());

    channel.pipeline().addBefore("via-decoder", "decompress", decompressor());
    receive(channel, compressionEnvelope(packet(
      PacketTypeRegistries.serverbound(),
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
      PacketTypeRegistries.clientbound(),
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

  private void assertLogged(String operation, Throwable failure, ConnectionState state, int protocol) {
    String output = errors.toString(StandardCharsets.UTF_8);
    String context = "Exception while " + operation + " packet";
    assertTrue(output.contains(context), output);
    assertEquals(output.indexOf(context), output.lastIndexOf(context), "Log each processing failure once");
    assertTrue(output.contains("state=" + state + ", protocol=" + protocol), output);
    assertTrue(output.contains("channel="), output);
    assertTrue(output.contains(failure.toString()), output);
    assertTrue(output.contains("\tat " + failure.getStackTrace()[0]), output);
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
    // Packet events need a user lookup; cache a test user without requiring a running Bukkit server.
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

  private static Binding bindAndPlay(EmbeddedChannel channel, int protocolVersion) {
    Binding binding = bindAndHandshake(channel, protocolVersion);
    send(channel, packet(PacketTypeRegistries.clientbound(), ConnectionState.LOGIN, PacketType.Server.SUCCESS, protocolVersion));
    if (binding.connectionState() == ConnectionState.LOGIN) {
      receive(channel, packet(PacketTypeRegistries.serverbound(), ConnectionState.LOGIN, PacketType.Client.LOGIN_ACK, protocolVersion));
      receive(channel, packet(PacketTypeRegistries.serverbound(), ConnectionState.CONFIGURATION, PacketType.Client.FINISH_CONFIGURATION, protocolVersion));
    }
    assertEquals(ConnectionState.PLAY, binding.connectionState());
    return binding;
  }

  private static ByteBuf heldSlot(int protocolVersion, int slot) {
    return packet(PacketTypeRegistries.serverbound(), ConnectionState.PLAY, PacketType.Client.HELD_ITEM_SLOT_IN, protocolVersion)
      .writeShort(slot);
  }

  private static void assertHeldSlot(EmbeddedChannel channel, int protocolVersion, int slot) {
    ByteBuf forwarded = channel.readInbound();
    try {
      assertEquals(1, forwarded.refCnt());
      assertEquals(PacketTypeRegistries.serverbound().idOf(ConnectionState.PLAY, PacketType.Client.HELD_ITEM_SLOT_IN, protocolVersion),
        ByteBufStreamCodecs.VARINT.decode(forwarded));
      assertEquals(slot, forwarded.readShort());
      assertFalse(forwarded.isReadable());
    } finally {
      forwarded.release();
    }
    assertNull(channel.readInbound());
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
    PacketTypeRegistry<?> registry,
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

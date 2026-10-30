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
import de.jpx3.intave.packet.contract.mutable.MutableHandshakePacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class BindingTest {
  private static final String RECEIVER = "intave-decoder";
  private static final String SENDER = "intave-encoder";

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
  void entersPlayAfterLegacyLoginSuccess() {
    int protocolVersion = 763;
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, protocolVersion);

    send(channel, packet(PacketIdRegistries.clientbound(), ConnectionState.LOGIN, "SUCCESS", protocolVersion));
    assertEquals(ConnectionState.PLAY, binding.connectionState());

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
  }

  @Test
  void followsConfigurationAcknowledgements() {
    int protocolVersion = 764;
    EmbeddedChannel channel = channelWithViaCodecs();
    Binding binding = bindAndHandshake(channel, protocolVersion);

    send(channel, packet(PacketIdRegistries.clientbound(), ConnectionState.LOGIN, "SUCCESS", protocolVersion));
    assertEquals(ConnectionState.LOGIN, binding.connectionState());

    receive(channel, packet(PacketIdRegistries.serverbound(), ConnectionState.LOGIN, "LOGIN_ACK", protocolVersion));
    assertEquals(ConnectionState.CONFIGURATION, binding.connectionState());

    receive(channel, packet(
      PacketIdRegistries.serverbound(),
      ConnectionState.CONFIGURATION,
      "FINISH_CONFIGURATION",
      protocolVersion
    ));
    assertEquals(ConnectionState.PLAY, binding.connectionState());

    receive(channel, packet(
      PacketIdRegistries.serverbound(),
      ConnectionState.PLAY,
      "CONFIGURATION_ACK",
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

    channel.pipeline().addBefore("via-decoder", "decompress", new ChannelInboundHandlerAdapter());
    channel.pipeline().addBefore("via-encoder", "compress", new ChannelOutboundHandlerAdapter());

    assertTrue(channel.writeInbound(Unpooled.buffer().writeByte(1)));
    assertTrue(channel.writeOutbound(Unpooled.buffer().writeByte(2)));
    release(channel.readInbound());
    release(channel.readOutbound());

    channel.runPendingTasks();
    assertImmediatelyBefore(channel, RECEIVER, "via-decoder");
    assertImmediatelyBefore(channel, SENDER, "via-encoder");

    assertTrue(channel.writeInbound(Unpooled.buffer().writeByte(3)));
    assertTrue(channel.writeOutbound(Unpooled.buffer().writeByte(4)));
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

    send(channel, packet(PacketIdRegistries.clientbound(), ConnectionState.LOGIN, "SUCCESS", protocolVersion));
    receive(channel, packet(PacketIdRegistries.serverbound(), ConnectionState.LOGIN, "LOGIN_ACK", protocolVersion));
    assertEquals(ConnectionState.CONFIGURATION, binding.connectionState());

    channel.pipeline().addBefore("via-decoder", "decompress", new ChannelInboundHandlerAdapter());
    receive(channel, compressionEnvelope(packet(
      PacketIdRegistries.serverbound(),
      ConnectionState.CONFIGURATION,
      "FINISH_CONFIGURATION",
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

    channel.pipeline().addBefore("via-encoder", "compress", new ChannelOutboundHandlerAdapter());
    send(channel, compressionEnvelope(packet(
      PacketIdRegistries.clientbound(),
      ConnectionState.LOGIN,
      "SUCCESS",
      protocolVersion
    )));

    assertEquals(ConnectionState.PLAY, binding.connectionState());
    channel.runPendingTasks();
    assertImmediatelyBefore(channel, SENDER, "via-encoder");

    binding.close();
    assertFalse(channel.finishAndReleaseAll());
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
    return binding;
  }

  private static ByteBuf handshake(int protocolVersion) {
    // Use a supported registry version even when testing an unknown advertised protocol.
    MutableHandshakePacket packet = WirePackets.createPacket(ConnectionState.HANDSHAKING, "SET_PROTOCOL", 764);
    packet.setProtocolVersion(protocolVersion);
    packet.setServerAddress("localhost");
    packet.setServerPort(25565);
    packet.setNextConnectionState(ConnectionState.LOGIN);
    ByteBuf handshake = Unpooled.buffer();
    WirePackets.encodePacket(PacketDirection.SERVERBOUND, ConnectionState.HANDSHAKING, "SET_PROTOCOL", 764, packet, handshake);
    return handshake;
  }

  private static ByteBuf packet(
    PacketIdRegistry registry,
    ConnectionState state,
    String typeName,
    int protocolVersion
  ) {
    ByteBuf packet = Unpooled.buffer();
    ByteBufStreamCodecs.VARINT.encode(packet, registry.idOf(state, typeName, protocolVersion));
    return packet;
  }

  private static ByteBuf compressionEnvelope(ByteBuf packet) {
    ByteBuf envelope = Unpooled.buffer(packet.readableBytes() + 1);
    ByteBufStreamCodecs.VARINT.encode(envelope, 0);
    envelope.writeBytes(packet);
    assertTrue(packet.release());
    return envelope;
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

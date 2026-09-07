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

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;

/**
 * {@link PayloadInView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction: every PacketEvents getter re-reads the packet
 * buffer, and {@code getData()} hands out a fresh array each call, so the payload body is wrapped
 * into a single {@link ByteBuf} that all reads share. That keeps the mark / reset reader index
 * dance the payload consumers perform working exactly as it does on ProtocolLib.
 */
public final class PacketEventsPayloadInView implements PayloadInView {

  private final PacketReceiveEvent event;
  private final String tag;
  private final ByteBuf data;

  private PacketEventsPayloadInView(PacketReceiveEvent event, String tag, ByteBuf data) {
    this.event = event;
    this.tag = tag;
    this.data = data;
  }

  /** @return a view over the event, or null when the packet is not a custom payload packet. */
  public static PacketEventsPayloadInView of(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.PLUGIN_MESSAGE) {
      return null;
    }
    WrapperPlayClientPluginMessage wrapper = new WrapperPlayClientPluginMessage(event);
    String channel = wrapper.getChannelName();
    if (channel == null) {
      channel = "error";
    } else if (channel.startsWith("minecraft:")) {
      channel = channel.substring(10);
    }
    byte[] payload = wrapper.getData();
    ByteBuf data = Unpooled.wrappedBuffer(payload == null ? new byte[0] : payload);
    return new PacketEventsPayloadInView(event, channel, data);
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public boolean cancelled() {
    return event.isCancelled();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public String tag() {
    return tag;
  }

  @Override
  public ByteBuf readBytes() {
    return data;
  }

  @Override
  public String readStringWithExtraByte() {
    try {
      data.markReaderIndex();
      // The legacy brand channel prefixes the string with its length; it is read and discarded,
      // exactly as the ProtocolLib reader does, and the rest of the buffer is decoded as UTF-8.
      data.readByte();
      return data.toString(StandardCharsets.UTF_8);
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    return "";
  }

  @Override
  public void release() {
    // Nothing held: the payload was copied out of the packet buffer at construction.
  }
}

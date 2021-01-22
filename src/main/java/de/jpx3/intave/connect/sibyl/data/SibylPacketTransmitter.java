package de.jpx3.intave.connect.sibyl.data;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.MinecraftKey;
import com.google.gson.JsonElement;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.connect.sibyl.LabyModChannelHelper;
import de.jpx3.intave.connect.sibyl.auth.SibylAuthentication;
import de.jpx3.intave.connect.sibyl.data.packet.SibylPacket;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.sync.Synchronizer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

public final class SibylPacketTransmitter {
  private final SibylAuthentication authentication;

  public SibylPacketTransmitter(SibylAuthentication authentication) {
    this.authentication = authentication;
  }

  @Native
  public void transmitPacket(Player player, SibylPacket sibylPacket) {
    String packetName = sibylPacket.packetName();
    JsonElement packetContent = sibylPacket.asJsonElement();
    transmitPacketDataToPlayer(player, "sibyl-packet-" + packetName, packetContent);
  }

  @Native
  private void transmitPacketDataToPlayer(Player player, String messageKey, JsonElement jsonElement) {
    String channel = "LMC";
    if(!authenticated(player)) {
      return;
    }
    PacketContainer packetContainer = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.CUSTOM_PAYLOAD);
    if(ProtocolLibAdapter.AQUATIC_UPDATE.atOrAbove()) {
      packetContainer.getSpecificModifier(MinecraftKey.class).write(0, new MinecraftKey(channel));
    } else {
      packetContainer.getStrings().write(0, channel);
    }
    try {
      byte[] bytesToSend = LabyModChannelHelper.getBytesToSend(messageKey, jsonElement == null ? null : jsonElement.toString());
      //noinspection unchecked
      Class<Object> packetDataSerializerClass = (Class<Object>) ReflectiveAccess.lookupServerClass("PacketDataSerializer");
      Object packetDataSerializer = packetDataSerializerClass.getConstructor(ByteBuf.class).newInstance(Unpooled.wrappedBuffer(bytesToSend));
      packetContainer.getSpecificModifier(packetDataSerializerClass).write(0, packetDataSerializer);
      Synchronizer.synchronize(() -> {
        try {
          ProtocolLibrary.getProtocolManager().sendServerPacket(player, packetContainer);
        } catch (InvocationTargetException e) {
          e.printStackTrace();
        }
      });
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      e.printStackTrace();
    }
  }

  @Native
  private boolean authenticated(Player player) {
    return authentication.isAuthenticated(player);
  }
}

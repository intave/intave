package de.jpx3.intave.connect.shadow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.MinecraftKey;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

public final class LabymodShadowIntegration {
  private final static int SHADOW_VERSION = 1;
  private final IntavePlugin plugin;

  private final LabymodClientListener shadowPacketListener;
  public LabymodShadowIntegration(IntavePlugin plugin) {
    this.plugin = plugin;
    this.shadowPacketListener = new LabymodClientListener(plugin, "info", this::processIncomingMessage);
  }

  @Native
  private void processIncomingMessage(Player player, JsonElement element) {
    JsonObject baseElement = element.getAsJsonObject();
    JsonObject shadow = baseElement.getAsJsonObject("shadow");
    if (shadow != null && shadow.get("enabled").getAsBoolean() && shadow.get("version").getAsInt() == SHADOW_VERSION) {
      enableShadow(player);
    }
  }

  public void setup() {
//    Bukkit.getOnlinePlayers().forEach(this::enableShadow);
  }

  @Native
  public void shutdown() {
    Bukkit.getOnlinePlayers().forEach(this::disableShadow);
  }

  @Native
  private void enableShadow(Player player) {
    // not ready yet
//    User user = UserRepository.userOf(player);
//    user.setShadow(true);
//    user.setShadowRepo(new ShadowPacketDataLink(user));
//    performShadowUpdate(player, ShadowStatus.ENABLE);
//    pipelineInjector.inject(player);
//
//    Synchronizer.synchronize(() -> {
//      player.sendMessage(ChatColor.GREEN + "Shadow enabled");
//    });
  }

  @Native
  private void disableShadow(Player player) {
    User user = UserRepository.userOf(player);
    boolean shadow = user.hasShadow();

    if(!shadow) {
      return;
    }

    user.setShadow(false);
    user.setShadowRepo(null);
    performShadowUpdate(player, ShadowStatus.DISABLE);
  }

  @Native
  private void performShadowUpdate(Player player, ShadowStatus update) {
    String channel = "SHADOW";
    PacketContainer packetContainer = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.CUSTOM_PAYLOAD);
    if(ProtocolLibAdapter.AQUATIC_UPDATE.atOrAbove()) {
      packetContainer.getSpecificModifier(MinecraftKey.class).write(0, new MinecraftKey(channel));
    } else {
      packetContainer.getStrings().write(0, channel);
    }
    try {
      byte[] bytesToSend = Unpooled.copyInt(update.key()).array();
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

  public void pushPacket(Player player, Object packet, ShadowContext movementData) {
    User user = UserRepository.userOf(player);
    ShadowPacketDataLink shadowPacketDataLink = user.shadowRepo();
    if(shadowPacketDataLink != null) {
      shadowPacketDataLink.save(packet, movementData);
    }
  }
}

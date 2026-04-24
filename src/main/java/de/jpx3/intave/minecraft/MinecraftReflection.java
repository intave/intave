package de.jpx3.intave.minecraft;

import de.jpx3.intave.klass.Lookup;

public final class MinecraftReflection {
  private MinecraftReflection() {}

  public static Class<?> getGameProfileClass() {
    return firstClass(
      "com.mojang.authlib.GameProfile",
      "net.minecraft.util.com.mojang.authlib.GameProfile"
    );
  }

  public static Class<?> getIChatBaseComponentClass() {
    return firstClass(
      "net.minecraft.network.chat.IChatBaseComponent",
      "net.minecraft.network.chat.Component",
      "{nms}.IChatBaseComponent",
      "IChatBaseComponent"
    );
  }

  public static Class<?> getPlayerInfoDataClass() {
    return firstClass(
      "{nms}.PacketPlayOutPlayerInfo$PlayerInfoData",
      "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry",
      "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$PlayerInfoEntry"
    );
  }

  public static Class<?> getBlockPositionClass() {
    return firstClass(
      "net.minecraft.core.BlockPos",
      "{nms}.BlockPosition",
      "BlockPosition"
    );
  }

  public static boolean isBlockPosition(Object value) {
    return value != null && getBlockPositionClass().isAssignableFrom(value.getClass());
  }

  public static Class<?> getMinecraftKeyClass() {
    return firstClass(
      "net.minecraft.resources.ResourceLocation",
      "{nms}.MinecraftKey",
      "MinecraftKey"
    );
  }

  public static Class<?> getPacketDataSerializerClass() {
    return firstClass(
      "net.minecraft.network.FriendlyByteBuf",
      "{nms}.PacketDataSerializer",
      "PacketDataSerializer"
    );
  }

  public static Class<?> getDataWatcherClass() {
    return firstClass(
      "net.minecraft.network.syncher.SynchedEntityData",
      "{nms}.DataWatcher",
      "DataWatcher"
    );
  }

  public static Class<?> firstClass(String... names) {
    for (String name : names) {
      Class<?> clazz = classByName(name);
      if (clazz != null) {
        return clazz;
      }
    }
    throw new IllegalStateException("Cannot resolve any Minecraft class from candidates");
  }

  public static Class<?> classByName(String name) {
    if (name == null || name.isEmpty()) {
      return null;
    }
    try {
      if (name.startsWith("{nms}.")) {
        return Lookup.serverClass(name.substring("{nms}.".length()));
      }
      if (name.indexOf('.') < 0) {
        return Lookup.serverClass(name);
      }
      return Class.forName(name);
    } catch (Throwable ignored) {
      return null;
    }
  }
}

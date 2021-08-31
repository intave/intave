package de.jpx3.intave.packet.converter;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.reflect.EquivalentConverter;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.*;
import de.jpx3.intave.adapter.MinecraftVersions;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public final class PlayerInfoDataConverter {
  private static Constructor<?> constructor;

  private static Class<?> gameProfileClass;
  private static Class<?> gameModeClass;
  private static Class<?> chatBaseComponentClass;

  private static final ThreadLocal<EquivalentConverter<PlayerInfoData>> converterThreadLocal =
    ThreadLocal.withInitial(PlayerInfoDataConverter::newConverter);

  public static EquivalentConverter<PlayerInfoData> threadConverter() {
    return converterThreadLocal.get();
  }

  public static EquivalentConverter<PlayerInfoData> newConverter() {
    return new EquivalentConverter<PlayerInfoData>() {
      public Object getGeneric(PlayerInfoData specific) {
        if (constructor == null) {
          try {
            List<Class<?>> args = new ArrayList<>();
            if (!MinecraftVersions.VER1_17_0.atOrAbove()) {
              args.add(PacketType.Play.Server.PLAYER_INFO.getPacketClass());
            }
            args.add(MinecraftReflection.getGameProfileClass());
            args.add(Integer.TYPE);
            args.add(EnumWrappers.getGameModeClass());
            args.add(MinecraftReflection.getIChatBaseComponentClass());
            constructor = MinecraftReflection.getPlayerInfoDataClass().getConstructor(args.toArray(new Class[0]));
          } catch (Exception var5) {
            throw new RuntimeException("Cannot find PlayerInfoData constructor.", var5);
          }
        }

        try {
          Object gameMode = EnumWrappers.getGameModeConverter().getGeneric(specific.getGameMode());
          Object displayName = specific.getDisplayName() != null ? specific.getDisplayName().getHandle() : null;
          return MinecraftVersions.VER1_17_0.atOrAbove() ?
            constructor.newInstance(specific.getProfile().getHandle(), specific.getLatency(), gameMode, displayName) :
            constructor.newInstance(null, specific.getProfile().getHandle(), specific.getLatency(), gameMode, displayName);
        } catch (Exception var4) {
          throw new RuntimeException("Failed to construct PlayerInfoData.", var4);
        }
      }

      public PlayerInfoData getSpecific(Object generic) {
        if (gameProfileClass == null) {
          gameProfileClass = MinecraftReflection.getGameProfileClass();
        }
        if (gameModeClass == null) {
          gameModeClass = EnumWrappers.getGameModeClass();
        }
        if (chatBaseComponentClass == null) {
          chatBaseComponentClass = MinecraftReflection.getIChatBaseComponentClass();
        }
        StructureModifier<Object> modifier = new StructureModifier<>(generic.getClass(), null, false).withTarget(generic);
        StructureModifier<WrappedGameProfile> gameProfiles = modifier.withType(gameProfileClass, BukkitConverters.getWrappedGameProfileConverter());
        WrappedGameProfile gameProfile = gameProfiles.read(0);
        StructureModifier<Integer> ints = modifier.withType(Integer.TYPE);
        int latency = ints.read(0);
        StructureModifier<EnumWrappers.NativeGameMode> gameModes = modifier.withType(gameModeClass, EnumWrappers.getGameModeConverter());
        EnumWrappers.NativeGameMode gameMode = gameModes.read(0);
        StructureModifier<WrappedChatComponent> displayNames = modifier.withType(chatBaseComponentClass, BukkitConverters.getWrappedChatComponentConverter());
        WrappedChatComponent displayName = displayNames.read(0);
        return new PlayerInfoData(gameProfile, latency, gameMode, displayName);
      }

      public Class<PlayerInfoData> getSpecificType() {
        return PlayerInfoData.class;
      }
    };
  }
}

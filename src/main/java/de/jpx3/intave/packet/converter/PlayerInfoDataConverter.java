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

  private static final Class<?> gameProfileClass = MinecraftReflection.getGameProfileClass();
  private static final Class<?> gameModeClass = EnumWrappers.getGameModeClass();
  private static final Class<?> chatBaseComponentClass = MinecraftReflection.getIChatBaseComponentClass();
  private static final EquivalentConverter<WrappedGameProfile> gameProfileConverter = BukkitConverters.getWrappedGameProfileConverter();
  private static final EquivalentConverter<EnumWrappers.NativeGameMode> gameModeConverter = EnumWrappers.getGameModeConverter();
  private static final EquivalentConverter<WrappedChatComponent> chatComponentConverter = BukkitConverters.getWrappedChatComponentConverter();

  private static final ThreadLocal<EquivalentConverter<List<PlayerInfoData>>> converterThreadLocal =
    ThreadLocal.withInitial(PlayerInfoDataConverter::newConverter);

  public static EquivalentConverter<List<PlayerInfoData>> threadConverter() {
    return converterThreadLocal.get();
  }

  public static EquivalentConverter<List<PlayerInfoData>> newConverter() {
    EquivalentConverter<PlayerInfoData> converter = new EquivalentConverter<PlayerInfoData>() {
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

      private StructureModifier<Object> gameProfileModifier;

      public synchronized PlayerInfoData getSpecific(Object generic) {
        if (gameProfileModifier == null) {
          gameProfileModifier = new StructureModifier<>(generic.getClass(), null, false);
        }
        StructureModifier<Object> modifier = gameProfileModifier.withTarget(generic);
        StructureModifier<WrappedGameProfile> gameProfiles = modifier.withType(gameProfileClass, gameProfileConverter);
        WrappedGameProfile gameProfile = gameProfiles.readSafely(0);
        if (gameProfile == null) {
          throw new RuntimeException("Cannot find game profile.");
        }
        StructureModifier<Integer> ints = modifier.withType(Integer.TYPE);
        if (ints.size() < 1) {
          throw new RuntimeException("Cannot find latency.");
        }
        int latency = ints.read(0);
        StructureModifier<EnumWrappers.NativeGameMode> gameModes = modifier.withType(gameModeClass, gameModeConverter);
        EnumWrappers.NativeGameMode gameMode = gameModes.read(0);
        StructureModifier<WrappedChatComponent> displayNames = modifier.withType(chatBaseComponentClass, chatComponentConverter);
        WrappedChatComponent displayName = displayNames.read(0);
        return new PlayerInfoData(gameProfile, latency, gameMode, displayName);
      }

      public Class<PlayerInfoData> getSpecificType() {
        return PlayerInfoData.class;
      }
    };
    return BukkitConverters.getListConverter(converter);
  }
}

package de.jpx3.intave.player.fake;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.entity.Player;

public final class MetadataAccess {
  private static final int SPRINT_BYTE = 3;

  public static void setSprinting(
    Player player,
    FakePlayerIdentity identity,
    boolean sprinting
  ) {
    updateFlag(player, identity, SPRINT_BYTE, sprinting);
  }

  private static final int SNEAK_BYTE = 1;

  public static void setSneaking(
    Player player,
    FakePlayerIdentity identity,
    boolean sneaking
  ) {
    updateFlag(player, identity, SNEAK_BYTE, sneaking);
  }

  public static void updateHealthFor(
    Player player,
    FakePlayerIdentity identity,
    float newHealth
  ) {
    identity.metadata(healthIndex(), EntityDataTypes.FLOAT, newHealth);
    updateMetadata(player, identity);
  }

  private static final int INVISIBLE_BYTE = 5;

  public static void updateVisibility(
    Player player,
    FakePlayerIdentity identity,
    boolean invisible
  ) {
    updateFlag(player, identity, INVISIBLE_BYTE, invisible);
  }

  private static void updateFlag(Player player, FakePlayerIdentity identity, int bit, boolean enabled) {
    byte current = metadataFlags(identity);
    boolean present = (current & 1 << bit) != 0;
    if (present == enabled) {
      return;
    }
    byte value = enabled ? (byte) (current | 1 << bit) : (byte) (current & ~(1 << bit));
    identity.metadata(0, EntityDataTypes.BYTE, value);
    updateMetadata(player, identity);
  }

  private static byte metadataFlags(FakePlayerIdentity identity) {
    Object value = identity.metadataValue(0);
    return value instanceof Byte ? (Byte) value : 0;
  }

  private static void updateMetadata(Player player, FakePlayerIdentity identity) {
    PacketEvents.getAPI().getPlayerManager().sendPacket(
      player,
      new WrapperPlayServerEntityMetadata(identity.identifier(), identity.metadata())
    );
  }

  static int healthIndex() {
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      return 9;
    } else if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      return 8;
    } else if (MinecraftVersions.VER1_10_0.atOrAbove()) {
      return 7;
    } else {
      return 6;
    }
  }
}

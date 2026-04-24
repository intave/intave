package de.jpx3.intave.block.variant.index;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class PacketEventsBlockStateIndexer {
  private static final int MAX_GLOBAL_BLOCK_STATE_ID = 65535;
  private static final int MAX_CONSECUTIVE_MISSES = 2048;

  private static Map<Material, Map<Object, Integer>> indexedStates;

  private PacketEventsBlockStateIndexer() {
  }

  static Map<Object, Integer> index(Material material) {
    ensureIndexed();
    Map<Object, Integer> variants = indexedStates.get(material);
    if (variants == null || variants.isEmpty()) {
      variants = new HashMap<>();
      variants.put(defaultStateOf(material), 0);
    }
    return new HashMap<>(variants);
  }

  private static synchronized void ensureIndexed() {
    if (indexedStates != null) {
      return;
    }
    ClientVersion version = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    Map<Material, Map<Object, Integer>> statesByMaterial = new EnumMap<>(Material.class);
    int consecutiveMisses = 0;
    boolean foundNonAir = false;
    for (int globalId = 0; globalId <= MAX_GLOBAL_BLOCK_STATE_ID; globalId++) {
      WrappedBlockState blockState = WrappedBlockState.getByGlobalId(version, globalId, false);
      if (globalId != 0 && blockState.getType() == StateTypes.AIR) {
        if (foundNonAir && ++consecutiveMisses > MAX_CONSECUTIVE_MISSES) {
          break;
        }
        continue;
      }
      if (blockState.getType() != StateTypes.AIR) {
        foundNonAir = true;
        consecutiveMisses = 0;
      }
      Material material = materialOf(blockState);
      if (material == null || !material.isBlock()) {
        continue;
      }
      Map<Object, Integer> states = statesByMaterial.computeIfAbsent(material, ignored -> new HashMap<>());
      states.put(blockState.clone(), isDefault(version, blockState) ? 0 : globalId);
    }
    indexedStates = statesByMaterial;
  }

  private static boolean isDefault(ClientVersion version, WrappedBlockState blockState) {
    return WrappedBlockState.getDefaultState(version, blockState.getType(), false).equals(blockState);
  }

  private static WrappedBlockState defaultStateOf(Material material) {
    ClientVersion version = PacketEvents.getAPI().getServerManager().getVersion().toClientVersion();
    return WrappedBlockState.getByString(version, createBlockData(material).getAsString(false), true);
  }

  private static BlockData createBlockData(Material material) {
    try {
      return (BlockData) Bukkit.class.getMethod("createBlockData", Material.class).invoke(null, material);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to create block data for " + material, exception);
    }
  }

  private static Material materialOf(WrappedBlockState blockState) {
    String name = blockState.getType().getName();
    int namespace = name.indexOf(':');
    String key = namespace >= 0 ? name.substring(namespace + 1) : name;
    return Material.matchMaterial(key.toUpperCase(Locale.ROOT));
  }
}

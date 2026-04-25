package de.jpx3.intave.module.filter;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityIterable;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.SubstitutionIterator;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.ENTITY_NBT_QUERY;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class EntityIdFilter extends Filter {
  private static final String RELOAD_METADATA_KEY = "intave::DT6NyhqI5bPJTeQRO6KWl1hNcEy2YZAp";

  public EntityIdFilter() {
    super("entityid");
    setup();
  }

  public void setup() {
    ShutdownTasks.addBeforeAll(this::shutdown);
    for (Player player : Bukkit.getOnlinePlayers()) {
      Plugin owningPlugin = null;
      for (MetadataValue metadata : player.getMetadata(RELOAD_METADATA_KEY)) {
        Map<Integer, Integer> translations = (Map<Integer, Integer>) metadata.value();
        if (translations != null && metadata.getOwningPlugin().getName().equalsIgnoreCase("Intave")) {
          UserRepository.userOf(player).meta().connection().insertIdTranslations(translations);
          owningPlugin = metadata.getOwningPlugin();
          break;
        }
      }
      if (owningPlugin != null) {
        player.removeMetadata(RELOAD_METADATA_KEY, owningPlugin);
      }
    }
  }

  public void shutdown() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      JavaPlugin intave = IntavePlugin.singletonInstance();
      Map<Integer, Integer> translation = new HashMap<>(UserRepository.userOf(player).meta().connection().globalEntityIdsToLocalIds());
      player.removeMetadata(RELOAD_METADATA_KEY, intave);
      player.setMetadata(RELOAD_METADATA_KEY, new FixedMetadataValue(intave, translation));
    }
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY,
      ENTITY_NBT_QUERY
    },
    priority = ListenerPriority.LOWEST
  )
  public void onPacket(
    ProtocolPacketEvent event
  ) {
    User user = UserRepository.userOf(event.getPlayer());
    PacketContainer packet = event.getPacket();
    StructureModifier<Integer> ints = packet.getIntegers();
    int localId = ints.read(0);
    int globalId = user.meta().connection().globalEntityIdFromLocal(localId);
    ints.write(0, globalId);
  }

  @PacketSubscription(
    packetsOut = {
      ATTACH_ENTITY,
      BED,
      BLOCK_BREAK_ANIMATION,
      CAMERA,
      COLLECT,
      COMBAT_EVENT,
      ENTITY,
      ENTITY_DESTROY,
      ENTITY_EFFECT,
      ENTITY_EQUIPMENT,
      ENTITY_HEAD_ROTATION,
      ENTITY_LOOK,
      ENTITY_METADATA,
      ENTITY_MOVE_LOOK,
      ENTITY_SOUND,
      ENTITY_STATUS,
      ENTITY_TELEPORT,
      ENTITY_VELOCITY,
      LOOK_AT,
      LOGIN,
      MOUNT,
      NAMED_ENTITY_SPAWN,
      OPEN_WINDOW,
      OPEN_WINDOW_HORSE,
      REL_ENTITY_MOVE,
      REL_ENTITY_MOVE_LOOK,
      REMOVE_ENTITY_EFFECT,
      SPAWN_ENTITY,
      SPAWN_ENTITY_EXPERIENCE_ORB,
      SPAWN_ENTITY_LIVING,
      SPAWN_ENTITY_PAINTING,
      SPAWN_ENTITY_WEATHER,
      UPDATE_ATTRIBUTES,
      UPDATE_ENTITY_NBT,
      USE_BED
    },
    priority = ListenerPriority.HIGHEST
  )
  public void onPacketOut(
    ProtocolPacketEvent event
  ) {
    PacketContainer packet = event.getPacket();
    User user = UserRepository.userOf(event.getPlayer());
    ConnectionMetadata connection = user.meta().connection();
    EntityIterable entities = PacketReaders.readerOf(packet);
    boolean isDestroy = packet.getType() == PacketType.Play.Server.ENTITY_DESTROY;
    for (SubstitutionIterator<Integer> iterator = entities.iterator(); iterator.hasNext(); ) {
      Integer globalId = iterator.next();
      Integer localId = connection.localEntityIdFromGlobal(globalId);
      if (localId == -1) {
        localId = connection.newLocalIdFor(globalId);
      }
      iterator.set(localId);
    }
    if (isDestroy) {
      Set<Integer> toRemove = new HashSet<>();
      entities.forEach(toRemove::add);
      toRemove.forEach(connection::markIdAsDeprecated);
      user.tickFeedback(() ->
        user.tickFeedback(() ->
          toRemove.forEach(connection::removeId)
        ));
    }
    entities.release();
  }

  @Override
  protected boolean enabled() {
    return false;
  }
}

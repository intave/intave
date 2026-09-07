package de.jpx3.intave.module.filter;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityIterable;
import de.jpx3.intave.packet.view.EntityInteractIdView;
import de.jpx3.intave.packet.view.PacketEventsEntityInteractIdView;
import de.jpx3.intave.packet.view.PacketEventsEntityNbtQueryIdView;
import de.jpx3.intave.packet.view.ProtocolLibEntityInteractIdView;
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

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
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
      ATTACK_ENTITY,
      USE_ENTITY,
      ENTITY_NBT_QUERY
    },
    priority = ListenerPriority.LOWEST
  )
  public void onPacket(
    PacketEvent event
  ) {
    translateEntityId(new ProtocolLibEntityInteractIdView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      ATTACK_ENTITY,
      USE_ENTITY,
      ENTITY_NBT_QUERY
    },
    priority = ListenerPriority.LOWEST
  )
  public void onPacket(
    PacketReceiveEvent event
  ) {
    // ProtocolLib reaches the target of an interaction and the target of an NBT query through the
    // same packet field; PacketEvents needs the matching wrapper per packet type, so the view is
    // picked by whichever one claims the event.
    EntityInteractIdView view = PacketEventsEntityInteractIdView.of(event);
    if (view == null) {
      view = PacketEventsEntityNbtQueryIdView.of(event);
    }
    if (view == null) {
      return;
    }
    translateEntityId(view);
  }

  /** Engine independent local to global id translation; see {@link EntityInteractIdView}. */
  private void translateEntityId(EntityInteractIdView view) {
    Player player = view.player();
    Integer localId = player == null ? null : view.entityId();
    if (localId == null) {
      view.release();
      return;
    }
    User user = UserRepository.userOf(player);
    int globalId = user.meta().connection().globalEntityIdFromLocal(localId);
    view.setEntityId(globalId);
    view.release();
  }

  /**
   * Not twinned onto PacketEvents. This subscription does not read a field: it hands the packet to
   * {@link PacketReaders#readerOf(PacketContainer)}, which dispatches on the ProtocolLib packet
   * type into one of the {@code de.jpx3.intave.packet.reader} readers and returns a mutable
   * {@link EntityIterable} over however many entity ids that particular packet carries. Every one
   * of the 34 packet types listed below needs its own reader, several of them - the paintings and
   * weather spawns, the horse window, the bed packets, the NBT update - have no PacketEvents
   * wrapper at all on the protocol range Intave supports, and the ENTITY_DESTROY branch below
   * additionally re-reads the iterable after the substitution. Porting that is a second reader
   * layer, not a view, so it is left on ProtocolLib.
   */
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
    PacketEvent event
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

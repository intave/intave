package de.jpx3.intave.module.filter;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.view.EntityHealthView;
import de.jpx3.intave.packet.view.PacketEventsEntityHealthView;
import de.jpx3.intave.packet.view.ProtocolLibEntityHealthView;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_METADATA;

//@Deprecated
public final class HealthFilter extends Filter {
  private final IntavePlugin plugin;

  public HealthFilter(IntavePlugin plugin) {
    super("health");
    this.plugin = plugin;
  }

  @PacketSubscription(
    packetsOut = {
      ENTITY_METADATA
    },
    priority = ListenerPriority.NORMAL
  )
  public void depriveHealth(PacketEvent event) {
    // The deep clone Rule #3151235 demands happens inside the view's constructor.
    handleMetadata(new ProtocolLibEntityHealthView(event));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsOut = {
      ENTITY_METADATA
    },
    priority = ListenerPriority.NORMAL
  )
  public void depriveHealth(PacketSendEvent event) {
    PacketEventsEntityHealthView view = PacketEventsEntityHealthView.of(event);
    if (view == null) {
      return;
    }
    handleMetadata(view);
  }

  /** Engine independent health obscuring; see {@link EntityHealthView}. */
  private void handleMetadata(EntityHealthView view) {
    Entity entity = view.entity();
    if (entity == null || entity instanceof EnderDragon || entity instanceof Wither) {
      // Boss health drives the client's boss bar, so it has to stay honest.
      view.discard();
      return;
    }
    Player player = view.player();
    if (entity instanceof LivingEntity
      && player != null
      && entity.getEntityId() != player.getEntityId()) {
      view.obscureHealth(createFakeHealth());
    }
    view.release();
  }

  private float createFakeHealth() {
    return Math.max(1, (float) (Math.random() * 20.0F));
  }

  @Override
  protected boolean enabled() {
    return !MinecraftVersions.VER1_19.atOrAbove() && super.enabled();
  }
}

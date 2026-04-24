package de.jpx3.intave.module.filter;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.entity.EntityLookup;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;

import java.util.List;

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
  public void depriveHealth(ProtocolPacketEvent event, WrapperPlayServerEntityMetadata packet) {
    Player player = event.getPlayer();
    Entity entity = EntityLookup.findEntity(player.getWorld(), packet.getEntityId());
    if (entity == null || entity instanceof EnderDragon || entity instanceof Wither) {
      return;
    }
    List<EntityData<?>> metadata = packet.getEntityMetadata();
    if (entity instanceof LivingEntity && entity.getEntityId() != player.getEntityId()) {
      if (metadata != null) {
        for (EntityData<?> data : metadata) {
          stripHealthFrom(data);
        }
      }
    }
    event.markForReEncode(true);
  }

  @SuppressWarnings("unchecked")
  private void stripHealthFrom(EntityData<?> data) {
    if (data != null && data.getIndex() == 6 && data.getValue() instanceof Float && (float) data.getValue() != 0.0F) {
      ((EntityData<Float>) data).setValue(createFakeHealth());
    }
  }

  private float createFakeHealth() {
    return Math.max(1, (float) (Math.random() * 20.0F));
  }

  @Override
  protected boolean enabled() {
    return !MinecraftVersions.VER1_19.atOrAbove() && super.enabled();
  }
}

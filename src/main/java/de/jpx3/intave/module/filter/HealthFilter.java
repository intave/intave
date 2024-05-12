package de.jpx3.intave.module.filter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityMetadataReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

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
  public void depriveHealth(PacketEvent event) {
    // Rule #3151235: When editing metadata, do a deepClone().
    // Why? I still don't know after 5 hours of debugging.
    event.setPacket(event.getPacket().deepClone());
    PacketContainer packet = event.getPacket();
    EntityMetadataReader reader = PacketReaders.readerOf(packet);
    Entity entity = reader.entityBy(event);
    if (entity == null) {
      reader.release();
      return;
    }
    if (entity instanceof EnderDragon) {
      reader.release();
      return;
    }
    List<WrappedWatchableObject> watchables = reader.metadataObjects();
    if (entity instanceof LivingEntity && entity.getEntityId() != event.getPlayer().getEntityId()) {
      if (watchables != null) {
//        WrappedWatchableObject healthWatchable = null;
        for (int i = 0; i < watchables.size(); i++) {
          WrappedWatchableObject watchable = watchables.get(i);
          if (watchable.getIndex() == 6 && watchable.getValue() instanceof Float) {
            watchable = new WrappedWatchableObject(watchable.getIndex(), watchable.getRawValue());
            stripHealthFrom(watchable);
            watchables.set(i, watchable);
//            System.out.println("Stripped health from " + entity.getName() + " (" + entity.getEntityId() + ") " + "receiver id: " + event.getPlayer().getEntityId());
          }
        }
      }
    }
    reader.setMetadataObjects(watchables);
    reader.release();
  }

  private void stripHealthFrom(WrappedWatchableObject watchable) {
    if (watchable != null && watchable.getIndex() == 6 && watchable.getRawValue() instanceof Float && (float) watchable.getRawValue() != 0.0F) {
      watchable.setValue(createFakeHealth());
    }
  }

  private float createFakeHealth() {
    float fakeHealth;
    if (chanceOf(0.05f)) {
      fakeHealth = Float.POSITIVE_INFINITY;
    } else if (chanceOf(0.05f)) {
      fakeHealth = Math.max(1, (float) (Math.random() * 20.0F));
    } else {
      fakeHealth = Float.NaN;
    }
    return fakeHealth;
  }

  private boolean chanceOf(float chance) {
    return Math.random() < chance;
  }

  @Override
  protected boolean enabled() {
    if (MinecraftVersions.VER1_19.atOrAbove()) {
      return false;
    }
//    return false;
    return !IntaveControl.GOMME_MODE && super.enabled();
  }
}

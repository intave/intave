package de.jpx3.intave.module.filter;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.ENTITY_METADATA;

public final class HealthFilter extends Filter {
  private final IntavePlugin plugin;

  public HealthFilter(IntavePlugin plugin) {
    super("health");
    this.plugin = plugin;
  }

  @PacketSubscription(
    packetsOut = {
      ENTITY_METADATA
    }
  )
  public void depriveHealth(PacketEvent event) {
    if (!enabled()) {
      return;
    }
    try {
      if (event.getPacket().getIntegers().getValues().isEmpty()) {
        return;
      }
      PacketContainer packet = event.getPacket();
      EntityReader entityReader = PacketReaders.readerOf(packet);
      Entity entity = entityReader.entityBy(event);
      entityReader.release();
      if (entity == null) {
        return;
      }
      if (entity instanceof LivingEntity && entity.getUniqueId() != event.getPlayer().getUniqueId())
        if (packet.getWatchableCollectionModifier().read(0) != null) {
          packet = packet.deepClone();
          event.setPacket(packet);
          if (event.getPacket().getType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrappedDataWatcher watcher = new WrappedDataWatcher(packet.getWatchableCollectionModifier().read(0));
            stripHealthFromDataWatcher(watcher);
            packet.getWatchableCollectionModifier().write(0, watcher.getWatchableObjects());
          }
        }
    } catch (Exception ignored) {
    }
  }

  private void stripHealthFromDataWatcher(WrappedDataWatcher watcher) {
    if (watcher != null && watcher.getObject(6) != null && watcher.getFloat(6) != 0.0F) {
      float fakeHealth;
      if (chanceOf(0.05f)) {
        fakeHealth = Float.POSITIVE_INFINITY;
      } else if (chanceOf(0.05f)) {
        fakeHealth = Math.max(1, (float) (Math.random() * 20.0F));
      } else {
        fakeHealth = Float.NaN;
      }
      watcher.setObject(6, fakeHealth);
    }
  }

  private boolean chanceOf(float chance) {
    return Math.random() < chance;
  }

  @Override
  protected boolean enabled() {
    return false;
//    return super.enabled();
  }
}

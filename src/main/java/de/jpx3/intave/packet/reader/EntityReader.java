package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.entity.EntityLookup;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

public class EntityReader extends AbstractPacketReader {
  public int entityId() {
    return packet.getIntegers().read(0);
  }

  public @Nullable Entity entityBy(PacketEvent event) {
    return entityBy(event.getPlayer().getWorld());
  }

  public @Nullable Entity entityBy(World world) {
    int identifier = packet.getIntegers().read(0);
    return EntityLookup.findEntity(world, identifier);
  }
}

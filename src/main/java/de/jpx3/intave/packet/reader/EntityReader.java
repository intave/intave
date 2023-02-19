package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.entity.EntityLookup;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

public class EntityReader extends AbstractPacketReader implements EntityIterable {
  public int entityId() {
    return packet().getIntegers().read(0);
  }

  public @Nullable Entity entityBy(PacketEvent event) {
    return entityBy(event.getPlayer().getWorld());
  }

  public @Nullable Entity entityBy(World world) {
    return EntityLookup.findEntity(world, entityId());
  }

  private boolean pendingIdAccess = true;

  @NotNull
  @Override
  public SubstitutionIterator<Integer> iterator() {
    pendingIdAccess = true;
    return STATIC_ITERATOR;
  }

  @Override
  public void forEach(Consumer<? super Integer> action) {
    action.accept(entityId());
  }

  @Override
  public Spliterator<Integer> spliterator() {
    return Spliterators.spliterator(iterator(), 1, 0);
  }

  private final SubstitutionIterator<Integer> STATIC_ITERATOR = new SubstitutionIterator<Integer>() {
    @Override
    public boolean hasNext() {
      return pendingIdAccess;
    }

    @Override
    public Integer next() {
      pendingIdAccess = false;
      return packet().getIntegers().read(0);
    }

    @Override
    public void set(Integer integer) {
      packet().getIntegers().write(0, integer);
    }
  };
}

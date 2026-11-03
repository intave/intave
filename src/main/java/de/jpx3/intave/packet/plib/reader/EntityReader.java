/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.plib.reader;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.entity.EntityLookup;
import de.jpx3.intave.user.User;
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

  public void setEntityId(int entityId) {
    StructureModifier<Integer> integers = packet().getIntegers();
    integers.writeSafely(0, entityId);
  }

  public @Nullable Entity entityBy(PacketEvent event) {
    return entityBy(event.getPlayer().getWorld());
  }

  public @Nullable Entity entityBy(World world) {
    return EntityLookup.findEntity(world, entityId());
  }

  private boolean pendingIdAccess = true;

  public boolean targetEntityIdIsSameAs(User user) {
    return user.hasPlayer() && user.player().getEntityId() == entityId();
  }

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

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

package de.jpx3.intave.packet.view;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import de.jpx3.intave.packet.reader.EntityMetadataReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@link EntityHealthView} backed by ProtocolLib.
 * <p>
 * Reproduces the health filter's original mechanics exactly, quirks included:
 * <ul>
 *   <li>the packet is deep cloned in the constructor before anything else happens. Rule #3151235:
 *   when editing metadata, deep clone first. Why it is needed was never established, only that
 *   editing the original container misbehaves;</li>
 *   <li>a matched watchable is not mutated in place but rebuilt through
 *   {@code new WrappedWatchableObject(index, rawValue)} and swapped back into the list, which is
 *   what makes the edit stick across ProtocolLib's watchable and data value representations;</li>
 *   <li>the metadata list is read once and written back on {@link #release()} even when nothing
 *   changed, which is the order the original body used.</li>
 * </ul>
 */
public final class ProtocolLibEntityHealthView implements EntityHealthView {

  /** Legacy protocol index of the health metadata entry. */
  private static final int HEALTH_INDEX = 6;

  private final PacketEvent event;
  private final EntityMetadataReader reader;

  private List<WrappedWatchableObject> watchables;
  private boolean watchablesRead;

  public ProtocolLibEntityHealthView(PacketEvent event) {
    // Rule #3151235: when editing metadata, do a deepClone(). Why? Still unknown after 5 hours of
    // debugging - but the edit does not survive without it.
    event.setPacket(event.getPacket().deepClone());
    this.event = event;
    this.reader = PacketReaders.readerOf(event.getPacket());
  }

  /** @return the wrapped reader, for metadata code that still needs ProtocolLib specifics. */
  public EntityMetadataReader reader() {
    return reader;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public @Nullable Entity entity() {
    return reader.entityBy(event);
  }

  @Override
  public void obscureHealth(float health) {
    List<WrappedWatchableObject> objects = watchables();
    if (objects == null) {
      return;
    }
    for (int i = 0; i < objects.size(); i++) {
      WrappedWatchableObject watchable = objects.get(i);
      if (watchable == null
        || watchable.getIndex() != HEALTH_INDEX
        || !(watchable.getValue() instanceof Float)) {
        continue;
      }
      // Rebuild rather than mutate: see the class note.
      watchable = new WrappedWatchableObject(watchable.getIndex(), watchable.getRawValue());
      if (watchable.getRawValue() instanceof Float && (float) watchable.getRawValue() != 0.0F) {
        watchable.setValue(health);
      }
      objects.set(i, watchable);
    }
  }

  @Override
  public void discard() {
    // releaseSafe rather than release so an early release here stays idempotent.
    reader.releaseSafe();
  }

  @Override
  public void release() {
    // The original body read the metadata list before deciding whether to edit it and wrote it
    // back unconditionally afterwards, so reading here when nothing asked for it keeps that order.
    reader.setLegacyMetadataObjects(watchables());
    reader.releaseSafe();
  }

  private List<WrappedWatchableObject> watchables() {
    if (!watchablesRead) {
      watchables = reader.legacyMetadataObjects();
      watchablesRead = true;
    }
    return watchables;
  }
}

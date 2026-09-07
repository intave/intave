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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.packet.reader.EntityReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EntityGenericView} backed by ProtocolLib.
 * <p>
 * Wraps the pooled {@link EntityReader} exactly the way the existing call sites do, so reader
 * pooling and release semantics stay unchanged. The destroy stage write goes straight to the
 * {@link PacketContainer} captured at construction rather than through the reader, which keeps it
 * valid after {@link #release()} - the current consumer releases the reader before rewriting the
 * stage, and that ordering has to keep working.
 */
public final class ProtocolLibEntityGenericView implements EntityGenericView {

  private static final int DESTROY_STAGE_FIELD = 1;

  private final PacketEvent event;
  private final PacketContainer packet;
  private final EntityReader reader;

  public ProtocolLibEntityGenericView(PacketEvent event) {
    this.event = event;
    this.packet = event.getPacket();
    this.reader = PacketReaders.readerOf(packet);
  }

  /** @return the wrapped reader, for entity code that still needs ProtocolLib specifics. */
  public EntityReader reader() {
    return reader;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public int entityId() {
    return reader.entityId();
  }

  @Override
  public @Nullable Entity entity() {
    return reader.entityBy(event);
  }

  @Override
  public void setDestroyStage(int destroyStage) {
    packet.getIntegers().write(DESTROY_STAGE_FIELD, destroyStage);
  }

  @Override
  public void release() {
    // releaseSafe keeps this idempotent, so releasing early - as the breaking update consumer
    // does, right after resolving the entity - stays harmless.
    reader.releaseSafe();
  }
}

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
import de.jpx3.intave.packet.reader.EntityMetadataReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * {@link EntityMetadataView} backed by ProtocolLib. Wraps the pooled {@link EntityMetadataReader}
 * so the existing reader pooling and release semantics stay exactly as they were.
 * <p>
 * Callers that replace the packet first (the health filter deep clones before editing metadata)
 * must do so before constructing the view, because the reader binds to the packet the event holds
 * at construction time.
 */
public final class ProtocolLibEntityMetadataView implements EntityMetadataView {

  private final PacketEvent event;
  private final EntityMetadataReader reader;

  public ProtocolLibEntityMetadataView(PacketEvent event) {
    this(event, PacketReaders.readerOf(event.getPacket()));
  }

  /** Wraps a reader the subscription linker already injected, as the attack view does. */
  public ProtocolLibEntityMetadataView(PacketEvent event, EntityMetadataReader reader) {
    this.event = event;
    this.reader = reader;
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
  public int entityId() {
    return reader.entityId();
  }

  @Override
  public boolean targetEntityIdIsSameAs(User user) {
    return reader.targetEntityIdIsSameAs(user);
  }

  @Override
  public Object fetchRaw(int index) {
    return reader.fetchRaw(index);
  }

  @Override
  public Optional<BlockPosition> bedPosition() {
    return reader.bedPosition();
  }

  @Override
  public void release() {
    reader.release();
  }
}

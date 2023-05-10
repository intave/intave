package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.module.nayoro.event.*;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.security.LicenseAccess;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

class RecordEventSink extends EventSink {
  private long last = System.currentTimeMillis();
  private final Environment environment;
  private final DataOutput dataOutput;
  private final Set<Integer> entities = new HashSet<>();
  private boolean setup = false;

  public RecordEventSink(Environment environment, DataOutput dataOutput) {
    this.environment = environment;
    this.dataOutput = dataOutput;
  }

  public synchronized void setupIfNeeded() {
    if (!setup) {
      setup = true;
      try {
        dataOutput.writeUTF("INTAVE/SAMPLE");
        dataOutput.writeUTF(LicenseAccess.network());
        UUID id = UUID.randomUUID();
        dataOutput.writeLong(id.getMostSignificantBits());
        dataOutput.writeLong(id.getLeastSignificantBits());
        dataOutput.writeLong(System.currentTimeMillis());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      visit(new PlayerInitEvent(environment.mainPlayer()));
      visit(new PropertiesEvent(environment.properties()));
      environment.mainPlayer().applyIfUserPresent(user -> {
        for (Entity tracedEntity : user.meta().connection().tracedEntities()) {
          visit(new EntitySpawnEvent(tracedEntity.entityId(), tracedEntity.typeData().size(), tracedEntity.position.toPosition()));
        }
      });
    }
  }

  @Override
  public void visit(EntitySpawnEvent event) {
    entities.add(event.id());
    visitAny(event);
  }

  @Override
  public void visit(EntityMoveEvent event) {
    if (entities.contains(event.entityId())) {
      visitAny(event);
    }
  }

  @Override
  public void visit(EntityRemoveEvent event) {
    if (entities.remove(event.id())) {
      visitAny(event);
    }
  }

  @Override
  public void visitAny(Event event) {
    setupIfNeeded();
    try {
      int duration = (int) Math.min(Short.MAX_VALUE, System.currentTimeMillis() - last);
      last = System.currentTimeMillis();
      dataOutput.writeShort(duration);
      dataOutput.writeByte(EventRegistry.idOf(event));
      event.serialize(environment, dataOutput);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not serialize event " + event.getClass().getName(), exception);
    }
  }

  @Override
  public void close() {
    try {
      dataOutput.writeShort(0);
      dataOutput.writeByte(-1);
      if (dataOutput instanceof Closeable) {
        ((Closeable) dataOutput).close();
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not close data output", exception);
    }
  }
}

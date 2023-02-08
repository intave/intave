package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.module.nayoro.event.*;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.module.tracker.entity.Entity;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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
      int duration = (int) Math.max(Short.MAX_VALUE, System.currentTimeMillis() - last);
      dataOutput.writeShort(duration);
      dataOutput.writeByte(EventRegistry.idOf(event));
      event.serialize(environment, dataOutput);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not serialize event " + event.getClass().getName(), exception);
    }
    last = System.currentTimeMillis();
  }

  @Override
  public void close() {
    try {
      if (dataOutput instanceof Closeable) {
        ((Closeable) dataOutput).close();
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not close data output", exception);
    }
  }
}

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static de.jpx3.intave.module.nayoro.SampleFlags.*;

class RecordEventSink extends EventSink {
  private long last = System.currentTimeMillis();
  private final Environment environment;
  private final DataOutput dataOutput;
  private final Set<Integer> entities = new HashSet<>();
  private boolean setup = false;
  private final Classifier classifier;
  private final Lock writeLock = new ReentrantLock();
  private final boolean checkFullEventRead = true;

  public RecordEventSink(Environment environment, DataOutput dataOutput) {
    this.environment = environment;
    this.dataOutput = dataOutput;
    this.classifier = Classifier.UNKNOWN;
  }

  public RecordEventSink(Environment environment, DataOutput dataOutput, Classifier classifier) {
    this.environment = environment;
    this.dataOutput = dataOutput;
    this.classifier = classifier;
  }

  public synchronized void setupIfNeeded() {
    if (!setup) {
      setup = true;
      try {
        writeLock.lock();
        dataOutput.writeUTF("INTAVE/SAMPLE");
        dataOutput.writeUTF(LicenseAccess.network());
        UUID id = UUID.randomUUID();
        dataOutput.writeLong(id.getMostSignificantBits());
        dataOutput.writeLong(id.getLeastSignificantBits());
        dataOutput.writeLong(System.currentTimeMillis());
        int flags = 0;
        if (checkFullEventRead) {
          flags |= EVENT_ZERO_BYTE_APPEND;
        }
        switch (classifier) {
          case LEGIT:
            flags |= MARKED_LEGIT;
            break;
          case CHEAT:
            flags |= MARKED_CHEAT;
            break;
          case UNKNOWN:
            flags |= MARKED_UNKNOWN;
            break;
        }
        dataOutput.writeInt(flags);
      } catch (IOException exception) {
        throw new RuntimeException(exception);
      } finally {
        writeLock.unlock();
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
  public void visit(AttackEvent event) {
    if (isIdInContextCurrent(event.source()) && isIdInContextCurrent(event.target())) {
      visitAny(event);
    }
  }

  private boolean isIdInContextCurrent(int id) {
    return entities.contains(id) || environment.mainPlayer().id() == id;
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
      writeLock.lock();
      int duration = (int) Math.min(Short.MAX_VALUE, System.currentTimeMillis() - last);
      last = System.currentTimeMillis();
      dataOutput.writeShort(duration);
      dataOutput.writeByte(EventRegistry.idOf(event));
      event.serialize(environment, dataOutput);
      if (checkFullEventRead) {
        dataOutput.writeByte(0xa);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not serialize event " + event.getClass().getName(), exception);
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void close() {
    setupIfNeeded();
    try {
      writeLock.lock();
      dataOutput.writeShort(0);
      dataOutput.writeByte(-1);
      if (dataOutput instanceof Closeable) {
        ((Closeable) dataOutput).close();
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not close data output", exception);
    } finally {
      writeLock.unlock();
    }
  }
}

package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.detection.PrintStreamDetectionSubscription;
import de.jpx3.intave.module.nayoro.event.*;
import de.jpx3.intave.share.Position;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;

abstract class Playback extends SinkEnvironment {
  private final DataInputStream dataInputStream;
  private final Map<String, Boolean> properties = new HashMap<>();
  private final PlaybackPlayerContainer playbackPlayer = new PlaybackPlayerContainer(this, new PrintStreamDetectionSubscription(System.out));
  private final Map<Integer, Position> entityPositions = new HashMap<>();
  private final Map<Integer, Double> entityMovementThisTick = new HashMap<>();
  private int movementRefreshTicks = 0;
  private final Map<Integer, Boolean> inSight = new HashMap<>();
  private final Set<Integer> entityIds = new HashSet<>();
  private boolean readHeader = false;

  public Playback(DataInputStream stream) {
    this.dataInputStream = stream;
  }

  public abstract void start();

  public abstract void stop();

  protected Event nextEvent() {
    try {
      if (!readHeader) {
        readHeader = true;
        String headerData = dataInputStream.readUTF();
        if (!"INTAVE/SAMPLE".equalsIgnoreCase(headerData)) {
          throw new RuntimeException("Invalid header data");
        }
        dataInputStream.readUTF();
//        String id = dataInputStream.readUTF();
        String id = new UUID(dataInputStream.readLong(), dataInputStream.readLong()).toString();
        long millis = dataInputStream.readLong();
      }
      short offset = dataInputStream.readShort();
      int packetId = dataInputStream.readByte();
      if (offset == 0 && packetId == -1) {
        return null;
      }
      Event event = EventRegistry.eventOf(packetId);
      event.deserialize(this, dataInputStream);
      event.withOffset(offset);
//      System.out.println("Read event: " + event.getClass().getSimpleName() + " with offset " + offset);
      return event;
    } catch (IOException exception) {
      exception.printStackTrace();
      return null;
    }
  }

  @Override
  public PlayerContainer mainPlayer() {
    return playbackPlayer;
  }

  @Override
  public void visit(PropertiesEvent event) {
    properties.putAll(event.properties());
    visitAny(event);
  }

  @Override
  public void visit(EntityMoveEvent event) {
    int entityId = event.entityId();
    entityIds.add(entityId);
    Position position = entityPositions.get(entityId);
    if (position == null) {
      position = new Position();
    }
    double distance = 0.0;
    if (event.applyX()) {
      distance += Math.abs(position.getX() - event.x());
      position.setX(event.x());
    } else {
      event.setX(position.getX());
    }
    if (event.applyY()) {
      distance += Math.abs(position.getY() - event.y());
      position.setY(event.y());
    } else {
      event.setY(position.getY());
    }
    if (event.applyZ()) {
      distance += Math.abs(position.getZ() - event.z());
      position.setZ(event.z());
    } else {
      event.setZ(position.getZ());
    }
    distance = Math.min(distance, 1);
    entityPositions.put(entityId, position);
    double finalDistance = distance;
    entityMovementThisTick.compute(entityId, (id, movement) -> {
      if (movement == null) {
        movement = 0.0;
      }
      return movement + finalDistance;
    });
    inSight.compute(entityId, (id, last) -> event.inSight());
    visitAny(event);
  }

  @Override
  public void visit(PlayerMoveEvent event) {
    if (movementRefreshTicks++ >= 5) {
      entityMovementThisTick.clear();
      movementRefreshTicks = 0;
    }
    visitAny(event);
  }

  @Override
  public void visitAny(Event event) {
    playbackPlayer.visitSelect(event);
    Modules.linker().nayoroEvents().fireEvent(playbackPlayer, event);
  }

  @Override
  public boolean property(String name) {
    return properties.getOrDefault(name, false);
  }

  @Override
  public Set<Integer> entities() {
    return entityIds;
  }

  @Override
  public Position positionOf(int entity) {
    if (entity == mainPlayer().id()) {
      return mainPlayer().position();
    } else {
      return entityPositions.get(entity);
    }
  }

  public boolean entityMoved(int entity, double distance) {
    return entityMovementThisTick.getOrDefault(entity, 0.0) >= distance;
  }

  @Override
  public boolean inSight(int entity) {
    return inSight.getOrDefault(entity, false);
  }

  @Override
  public Map<String, Boolean> properties() {
    return properties;
  }
}

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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.*;
import ac.intave.samples.serial.JsonReader;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.share.Position;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

abstract class Playback extends SinkEnvironment {
  private final JsonReader recordingReader;
  private final Map<String, String> properties = new HashMap<>();
  private final PlaybackPlayerContainer playbackPlayer = new PlaybackPlayerContainer(this);
  private final Map<Integer, Position> entityPositions = new HashMap<>();
  private final Map<Integer, Double> entityMovementThisTick = new HashMap<>();
  private int movementRefreshTicks = 0;
  private final Map<Integer, Boolean> inSight = new HashMap<>();
  private final Set<Integer> entityIds = new HashSet<>();

  public Playback(InputStream stream) {
    try {
      this.recordingReader = new JsonReader(stream);
      Event firstEvent = recordingReader.nextEvent();
      if (!(firstEvent instanceof HeaderEvent)) {
        throw new IOException("Nayoro recording does not start with a header event");
      }
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to open Nayoro recording", exception);
    }
  }

  public abstract void start();

  public abstract void stop();

  protected Event nextEvent() {
    try {
      return recordingReader.nextEvent();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read Nayoro event", exception);
    }
  }

  protected void closeRecording() {
    try {
      recordingReader.close();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to close Nayoro recording", exception);
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
      position = Position.mutableEmpty();
    }
    Position recordedPosition = SampleTypes.position(event.position());
    double distance = 0.0;
    if (event.applyX()) {
      distance += Math.abs(position.getX() - recordedPosition.getX());
      position.setX(recordedPosition.getX());
    }
    if (event.applyY()) {
      distance += Math.abs(position.getY() - recordedPosition.getY());
      position.setY(recordedPosition.getY());
    }
    if (event.applyZ()) {
      distance += Math.abs(position.getZ() - recordedPosition.getZ());
      position.setZ(recordedPosition.getZ());
    }
    event.setPosition(SampleTypes.position(position.immutable()));
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
    return Boolean.parseBoolean(properties.get(name));
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
  public Map<String, String> properties() {
    return properties;
  }
}

package de.jpx3.intave.check.world.interaction;

import de.jpx3.intave.check.world.InteractionRaytrace;

public enum InteractionType {
  BREAK(InteractionRaytrace.ResponseType.CANCEL, false),
  START_BREAK(InteractionRaytrace.ResponseType.RAYTRACE_CAST, false),
  INTERACT(InteractionRaytrace.ResponseType.RAYTRACE_CAST, false),
  PLACE(InteractionRaytrace.ResponseType.RAYTRACE_CAST, false);

  final InteractionRaytrace.ResponseType response;
  final boolean bufferAvailable;

  InteractionType(InteractionRaytrace.ResponseType response, boolean bufferAvailable) {
    this.response = response;
    this.bufferAvailable = bufferAvailable;
  }

  public InteractionRaytrace.ResponseType response() {
    return response;
  }
}

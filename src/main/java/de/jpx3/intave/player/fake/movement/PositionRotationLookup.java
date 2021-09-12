package de.jpx3.intave.player.fake.movement;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class PositionRotationLookup {
  public static Location lookup(Location location, double distance) {
    location = location.clone();
    Vector direction = rotationAsVector(location.getYaw(), 0.0f);
    location.add(direction.multiply(-distance));
    return location;
  }

  public static Vector rotationAsVector(final float yaw, final float pitch) {
    Vector vector = new Vector();
    vector.setY(-Math.sin(Math.toRadians(pitch)));
    double xz = Math.cos(Math.toRadians(pitch));
    vector.setX(-xz * Math.sin(Math.toRadians(yaw)));
    vector.setZ(xz * Math.cos(Math.toRadians(yaw)));
    return vector;
  }
}
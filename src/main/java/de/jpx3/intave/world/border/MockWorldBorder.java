package de.jpx3.intave.world.border;

import org.bukkit.Location;
import org.bukkit.WorldBorder;

public final class MockWorldBorder implements WorldBorder {
  private float size = (float) 300000000;
  private Location center;

  public MockWorldBorder(Location center) {
    this.center = center;
  }


  @Override
  public void reset() {

  }

  @Override
  public double getSize() {
    return size;
  }

  @Override
  public void setSize(double v) {
    this.size = (float) v;
  }

  @Override
  public void setSize(double v, long l) {
    setSize((float) v);
  }

  @Override
  public Location getCenter() {
    return center;
  }

  @Override
  public void setCenter(double v, double v1) {
    this.center = new Location(null, v, 0, v1);
  }

  @Override
  public void setCenter(Location location) {
    this.center = location;
  }

  @Override
  public double getDamageBuffer() {
    return 0;
  }

  @Override
  public void setDamageBuffer(double v) {

  }

  @Override
  public double getDamageAmount() {
    return 0;
  }

  @Override
  public void setDamageAmount(double v) {

  }

  @Override
  public int getWarningTime() {
    return 0;
  }

  @Override
  public void setWarningTime(int i) {

  }

  @Override
  public int getWarningDistance() {
    return 0;
  }

  @Override
  public void setWarningDistance(int i) {

  }

  public static MockWorldBorder create() {
    return new MockWorldBorder(new Location(null, 0, 0, 0));
  }
}

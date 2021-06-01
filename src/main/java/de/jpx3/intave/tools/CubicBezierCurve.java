package de.jpx3.intave.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class CubicBezierCurve {
  private final Point2d bezA, bezB;
  private final Point2d start, end;

  public CubicBezierCurve(
    double bezAx, double bezAz, double bezBx, double bezBz,
    double startX, double startZ, double endX, double endZ
  ) {
    this.bezA = new Point2d(bezAx, bezAz);
    this.bezB = new Point2d(bezBx, bezBz);
    this.start = new Point2d(startX, startZ);
    this.end = new Point2d(endX, endZ);
  }

  public Function<Double, Double> functional(double accuracy) {
    Map<Double, Point2d> interpolation = interpolate(accuracy);
    return x -> interpolation.get(Math.round(x * (1 / accuracy)) / (1 / accuracy)).posZ;
  }

  public Map<Double, Point2d> interpolate(double accuracy) {
    Map<Double, Point2d> traverse = new HashMap<>();
    for (double time = 0; time <= 1 + accuracy /* small overflow */; time += accuracy) {
      traverse.put(Math.round(time * (1 / accuracy)) / (1 / accuracy), animate(time));
    }
    return traverse;
  }

  public Point2d animate(double timestep) {
    double inverseTimestep = 1 - timestep;
    Point2d startBezierAMid = start.multiply(inverseTimestep).add(bezA.multiply(timestep));
    Point2d bezierMid = bezA.multiply(inverseTimestep).add(bezB.multiply(timestep));
    Point2d endBezierBMid = bezB.multiply(inverseTimestep).add(end.multiply(timestep));
    Point2d triangularLeft = startBezierAMid.multiply(inverseTimestep).add(bezierMid.multiply(timestep));
    Point2d triangularRight = bezierMid.multiply(inverseTimestep).add(endBezierBMid.multiply(timestep));
    return triangularLeft.multiply(inverseTimestep).add(triangularRight.multiply(timestep));
  }

  public static CubicBezierCurve identityCurve(double bezAx, double bezAz, double bezBx, double bezBz) {
    return new CubicBezierCurve(bezAx, bezAz, bezBx, bezBz, 0, 0, 1, 1);
  }

  private static class Point2d {
    private final double posX;
    private final double posZ;

    public Point2d(double posX, double posZ) {
      this.posX = posX;
      this.posZ = posZ;
    }

    public double posX() {
      return posX;
    }

    public double posZ() {
      return posZ;
    }

    public Point2d multiply(double multiplier) {
      return new Point2d(posX * multiplier, posZ * multiplier);
    }

    public Point2d add(Point2d other) {
      return new Point2d(posX + other.posX(), posZ + other.posZ());
    }
  }
}

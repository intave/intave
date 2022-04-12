package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.check;

import java.io.Serializable;

class Point implements Serializable {
  double x;
  double y;
  
  public Point(double x, double y) {
    this.x = x;
    this.y = y;
  }
}
package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

public class LogActivation extends ActivationFunction {
  @Override
  public double function(double input) {
    return Math.log(input);
  }
  
  @Override
  public double derivative(double input) {
    return Math.log(1 - input);
  }
}
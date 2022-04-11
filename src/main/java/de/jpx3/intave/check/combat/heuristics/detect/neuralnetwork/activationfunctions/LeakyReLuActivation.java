package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

public class LeakyReLuActivation extends ActivationFunction {
  @Override
  public double function(double input) {
    if (input > 0)
      return input;
    else
      return input * 0.01d;
  }
  
  @Override
  public double derivative(double input) {
    if (input > 0)
      return 1;
    // return input;
    else
      return input / 0.01d;
  }
}
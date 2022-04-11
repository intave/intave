package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

public class TestActivation extends ActivationFunction {
  @Override
  public double function(double input) {
    if(input > 0)
      return input;
    else
      return input % 1;
  }
  
  @Override
  public double derivative(double input) {
    if(input > 0)
      return input;
    else
      return input % 1;
  }
}
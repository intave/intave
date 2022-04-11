package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

public class SigmoidActivation extends ActivationFunction {
  @Override
  public double function(double input) {
    return sigmoid(input);
  }
  
  @Override
  public double derivative(double input) {
    return input * (1d - input);
  }
  
  public static double sigmoid(double input) {
    return 1d / (1d + Math.exp(-input));
  }
}
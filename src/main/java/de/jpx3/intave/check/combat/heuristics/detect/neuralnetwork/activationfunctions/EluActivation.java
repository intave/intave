package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

public class EluActivation extends ActivationFunction {
  public static double alpha = 1;
  
  @Override
  public double function(double input) {
    if (input >= 0)
      return input;
    else
      return alpha * (Math.pow(Math.E, input) - 1);
  }
  
  @Override
  public double derivative(double input) {
    if (input > 0)
      return 1;
    else
      return alpha * Math.exp(input);
  }
}
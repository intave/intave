package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;


public class SoftPlusActivation extends ActivationFunction {
  @Override
  public double function(double input) {
    return Math.log(1 + Math.pow(Math.E, input));
//    return Math.log(1d + Math.exp(input));
  }
  
  @Override
  public double derivative(double input) {
//    return Math.pow(Math.E, input) / (Math.pow(Math.E, input) + 1);
    return 1d / (Math.pow(Math.E, -input) + 1);
//    return 1d - (1d / (Math.pow(Math.E, input) + 1d));
  }
}
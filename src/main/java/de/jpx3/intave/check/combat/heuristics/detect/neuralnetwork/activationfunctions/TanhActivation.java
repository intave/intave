package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

public class TanhActivation extends ActivationFunction {
  @Override
  public double function(double input) {
    return Math.tanh(input);
  }
  
  @Override
  public double derivative(double input) {
//    return (Math.log(1 + input) - Math.log(1 - input)) / 2;
    return 2d / (Math.cosh(2 * input) + 1d);
  }
}
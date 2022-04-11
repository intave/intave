package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

public class GELUActivation extends ActivationFunction {
  // https://mlfromscratch.com/activation-functions-explained/#/
  @Override
  public double function(double input) {
    return 0.5d * input * (1 + Math.tanh(Math.sqrt(2/Math.PI*(input+0.044715*Math.pow(input, 3)))));
  }
  
  @Override
  public double derivative(double input) {
    double a = 0.0356774 * Math.pow(input, 3) + 0.797885 * input;
    return 0.5d * Math.tan(a) + (0.535161 * Math.pow(input, 3) + 0.398942 * input) * sechPow2(a) + 0.5d;
  }
  
  public static double sechPow2(double x) {
    return 4 * coshPow2(x) / Math.pow(Math.cosh(2 * x) + 1, 2);
  }
  public static double coshPow2(double x) {
    return 0.5 * (Math.cosh(2 * x) + 1);
  }
}
package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

public class SoftMaxActivation extends ActivationFunction {
  public double[] activationFunction(double[] input) {
    double[] output = new double[input.length];
  
    double sum = 0;
    for (int i = 0; i < input.length; i++)
      sum += Math.exp(input[i]);
  
    for (int i = 0; i < input.length; i++)
      output[i] = Math.exp(input[i]) / sum;
  
    return output;
  }
  
  public double[] dActivationFunction(double[] input) {
    double[] output = new double[input.length];
  
    double[] act = activationFunction(input);
    for (int i = 0; i < input.length; i++) {
      output[i] = act[i] * (1 - act[i]);
    }
  
    return output;
  }
  
  @Deprecated
  public double function(double input) {
    return 0;
  }
  @Deprecated
  public double derivative(double input) {
    return 0;
  }
}
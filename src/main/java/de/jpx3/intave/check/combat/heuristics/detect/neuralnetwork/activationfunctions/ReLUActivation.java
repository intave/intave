package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

public class ReLUActivation extends ActivationFunction {
  @Override
  public double function(double input) {
    if(input > 0)
      return input;
    else
      return 0;
    
//    return Math.max(0, input);
  }
  
  @Override
  public double derivative(double input) {
    if (input >= 0)
      return input;
    else
      return 0;
  }
}
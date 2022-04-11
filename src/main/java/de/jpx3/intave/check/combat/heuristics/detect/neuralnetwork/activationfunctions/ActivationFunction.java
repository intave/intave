package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions;

import java.io.Serializable;

public abstract class ActivationFunction implements Serializable {
  public static SigmoidActivation sigmoid = new SigmoidActivation();
  public static ReLUActivation reLU = new ReLUActivation();
  public static LeakyReLuActivation leakyReLu = new LeakyReLuActivation();
  public static EluActivation elu = new EluActivation();
  public static GELUActivation gelu = new GELUActivation();
  public static SoftPlusActivation softPlus = new SoftPlusActivation();
  public static TanhActivation tanh = new TanhActivation();
  public static TestActivation test = new TestActivation();
  public static LogActivation log = new LogActivation();
  
  public abstract double function(double input);
  
  public abstract double derivative(double input);
}
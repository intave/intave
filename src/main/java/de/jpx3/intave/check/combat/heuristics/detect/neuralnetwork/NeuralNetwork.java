package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork;

import de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions.ActivationFunction;
import de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.math.Matrix;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NeuralNetwork implements Serializable {
  public int trainedCounter = 0;
  public double localLearningRate = 0.03;
  public List<ActivationFunction> activationFunctions = new CopyOnWriteArrayList<>();
  public Matrix[] biases;
  public Matrix[] weights;
  public static double randomFrom = -1d;
  public static double randomTo = 1d;
  
  // the first is input, the last is output, the values in between are the number of nodes from hiddenlayers
  public NeuralNetwork(Object... inputValues) {
    List<Integer> inputNodeCounts = new ArrayList<>();
    for (Object inputValue : inputValues) {
      if (inputValue instanceof Integer) {
        inputNodeCounts.add((Integer) inputValue);
      } else if (inputValue instanceof ActivationFunction) {
        activationFunctions.add((ActivationFunction) inputValue);
      }
    }
    
    biases = new Matrix[inputNodeCounts.size() - 1];
    weights = new Matrix[inputNodeCounts.size() - 1];
    
    int lastLayerNodeCount = inputNodeCounts.get(0);
    for (int layerIndex = 0; layerIndex < biases.length; layerIndex++) {
      int nodeCount = inputNodeCounts.get(layerIndex + 1);
      
      Matrix bias = new Matrix(nodeCount, 1);
      bias.randomize(randomFrom, randomTo);
      biases[layerIndex] = bias;
      
      Matrix weight = new Matrix(nodeCount, lastLayerNodeCount);
      weight.randomize(randomFrom, randomTo);
      weights[layerIndex] = weight;
      
      lastLayerNodeCount = nodeCount;
    }
    
    while (inputNodeCounts.size() > activationFunctions.size()) {
      activationFunctions.add(ActivationFunction.sigmoid);
    }
  }
  
  public NeuralNetwork(Matrix[] weights, Matrix[] biases) {
    this.biases = biases;
    this.weights = weights;
    
    while (weights.length > activationFunctions.size()) {
      activationFunctions.add(ActivationFunction.sigmoid);
    }
  }
  
  public Matrix predict(double[] inputArray) {
    Matrix inputValues = Matrix.matrixOf1DimArray(inputArray);
    
    Matrix lastValues = inputValues;
    for (int layerIndex = 0; layerIndex < weights.length; layerIndex++) {
      lastValues = feedForwardLayer(layerIndex, lastValues);
    }
    
    return lastValues;
  }
  
  public Matrix feedForwardLayer(int layerIndex, Matrix lastValues) {
    Matrix weight = weights[layerIndex];
    Matrix bias = biases[layerIndex];
    
    // Generating the hidden outputs
    Matrix value = weight.multiplyDot(lastValues);
    value = value.add(bias);
    
    // Activation function
    value = value.forwardActivationFunction(activationFunctions.get(layerIndex));
    return value;
  }
  
  public void train(double[] inputArrayValues, double[] targetArrayValues) {
    trainedCounter++;
    Matrix inputValues = Matrix.matrixOf1DimArray(inputArrayValues);
    Matrix targetValues = Matrix.matrixOf1DimArray(targetArrayValues);
    
    Matrix[] layerInputOutputArray = new Matrix[weights.length + 1];
    Matrix lastLayerOutput = inputValues;
    layerInputOutputArray[0] = lastLayerOutput;
    for (int layerIndex = 0; layerIndex < weights.length; layerIndex++) {
      lastLayerOutput = feedForwardLayer(layerIndex, lastLayerOutput);
      layerInputOutputArray[layerIndex + 1] = lastLayerOutput;
    }
    
    Matrix outputErrors = targetValues.subtract(lastLayerOutput);
    Matrix lastErrors = outputErrors;
    for (int layerIndex = weights.length - 1; layerIndex >= 0; layerIndex--) {
      Matrix layerOutput = layerInputOutputArray[layerIndex + 1];
      Matrix gradiants = layerOutput.backwardsActivationFunction(activationFunctions.get(layerIndex));
      gradiants = gradiants.multiplyHadamard(lastErrors);
      gradiants = gradiants.multiplyScalar(getLearningRate());
      
      Matrix layerInput = layerInputOutputArray[layerIndex];
      Matrix layerInputTransposed = layerInput.transpose();
      Matrix deltaWeight = gradiants.multiplyDot(layerInputTransposed);
      
      weights[layerIndex] = weights[layerIndex].add(deltaWeight);
      
      biases[layerIndex] = biases[layerIndex].add(gradiants);
      
      Matrix transPosedWeights = weights[layerIndex].transpose();
      lastErrors = transPosedWeights.multiplyDot(lastErrors);
    }
  }
  
  double getLearningRate() {
    return localLearningRate;
  }
  
  public NeuralNetwork copy() {
    Matrix[] biases = new Matrix[this.biases.length];
    for (int i = 0; i < biases.length; i++) {
      biases[i] = this.biases[i].copy();
    }
    
    Matrix[] weights = new Matrix[this.weights.length];
    for (int i = 0; i < weights.length; i++) {
      weights[i] = this.weights[i].copy();
    }
    return new NeuralNetwork(weights, biases);
  }
  
  @Override
  public String toString() {
    return "NeuralNetwork{" +
      ", biases=" + Arrays.toString(biases) +
      ", weights=" + Arrays.toString(weights) +
      '}';
  }
}

class TestNeuralNetwork {
  static final double[][][] trainingData = new double[][][] {
    { { 1, 1 }, { 0 } },
    { { 1, 0 }, { 1 } },
    { { 0, 1 }, { 1 } },
    { { 0, 0 }, { 0 } }
  };
  
  public static void main(String[] args) {
    NeuralNetwork neuralNetwork = new NeuralNetwork(2, 2, 1);
    neuralNetwork.localLearningRate = 0.1d;

    JFrame frame = new JFrame();
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setSize(800, 800);
    double size = 50;
    BufferedImage bufferedImage = new BufferedImage((int) size, (int) size, BufferedImage.TYPE_INT_RGB);
    JPanel panel = new JPanel() {
      public void paint(Graphics graphics) {
        for (int x = 0; x < size; x++) {
          for (int y = 0; y < size; y++) {
            Matrix predict = neuralNetwork.predict(new double[] {
              x / size,
              y / size
            });
            int brightness = (int) (predict.data[0][0] * 255d);
            brightness = Math.min(brightness, 255);
            brightness = Math.max(brightness, 0);
            bufferedImage.setRGB(x, y, new Color(brightness, brightness, brightness).getRGB());
          }
        }
        graphics.drawImage(bufferedImage, 0, 0, getWidth(), getHeight(), null);
      }
    };
    frame.add(panel);
    frame.setVisible(true);
    frame.setLocationRelativeTo(null);
    
    new Thread(() -> {
      while (true) {
        for (int j = 0; j < 1; j++) {
          for (double[][] data : trainingData) {
            double[] inputs = data[0];
            double[] targets = data[1];
            
            neuralNetwork.train(inputs, targets);
          }
        }
        panel.repaint();
        try {
          Thread.sleep(1);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }).start();
  }
}
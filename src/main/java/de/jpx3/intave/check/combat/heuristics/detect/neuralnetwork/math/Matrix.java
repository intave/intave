package de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.math;

import de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions.ActivationFunction;
import de.jpx3.intave.check.combat.heuristics.detect.neuralnetwork.activationfunctions.SoftMaxActivation;

import java.io.Serializable;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class Matrix implements Serializable {
  final public double[][] data;
  
  public int rows() { // Zeile
    return data.length;
  }
  
  public int cols() { // Spalte
    return data[0].length;
  }
  
  public Matrix(double[][] data) {
    this.data = data;
  }
  
  public Matrix(int rows, int cols) {
    this.data = new double[rows][cols];
  }
  
  public Matrix transpose() {
    double[][] newData = new double[cols()][rows()];
    
    for (int rowsIndex = 0; rowsIndex < rows(); rowsIndex++) {
      for (int colsIndex = 0; colsIndex < cols(); colsIndex++) {
        newData[colsIndex][rowsIndex] = data[rowsIndex][colsIndex];
      }
    }
    
    return new Matrix(newData);
  }
  
  public Matrix copy() {
    double[][] copyData = Arrays.stream(data).map(double[]::clone).toArray(double[][]::new);
    return new Matrix(copyData);
  }
  
  /*
  Executes a function for every element of the Matrix.
   */
  public Matrix executeFunction(Function<Double, Double> function) {
    double[][] newData = new double[rows()][cols()];
  
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = function.apply(data[rowIndex][colIndex]);
      }
    }
    return new Matrix(newData);
  }
  
  public Matrix forwardActivationFunction(ActivationFunction activationFunction) {
    double[][] newData = new double[rows()][cols()];
    
    if(activationFunction instanceof SoftMaxActivation) {
      SoftMaxActivation softMax = (SoftMaxActivation) activationFunction;
      newData[0] = softMax.activationFunction(data[0]);
      return new Matrix(newData);
    }
    
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = activationFunction.function(data[rowIndex][colIndex]);
      }
    }
    return new Matrix(newData);
  }
  
  public Matrix backwardsActivationFunction(ActivationFunction dActivationFunction) {
    double[][] newData = new double[rows()][cols()];
  
    if(dActivationFunction instanceof SoftMaxActivation) {
      SoftMaxActivation softMax = (SoftMaxActivation) dActivationFunction;
      newData[0] = softMax.dActivationFunction(data[0]);
  
      return new Matrix(newData);
    }
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = dActivationFunction.derivative(data[rowIndex][colIndex]);
      }
    }
    return new Matrix(newData);
  }
  
  public Matrix merge(Matrix other) {
    double[][] newData = new double[rows()][cols()];
    
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        if (ThreadLocalRandom.current().nextBoolean()) {
          newData[rowIndex][colIndex] = data[rowIndex][colIndex];
        } else {
          newData[rowIndex][colIndex] = other.data[rowIndex][colIndex];
        }
      }
    }
    return new Matrix(newData);
  }
  
  public static Matrix matrixOf1DimArray(double[] input) {
    double[][] newData = new double[input.length][1];
    for (int index = 0; index < input.length; index++) {
      newData[index][0] = input[index];
    }
    return new Matrix(newData);
  }
  
  public Matrix randomize(double from, double to) {
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        data[rowIndex][colIndex] = ThreadLocalRandom.current().nextDouble(from, to);
      }
    }
    return this;
  }
  
  public Matrix add(double other) {
    double[][] newData = new double[rows()][cols()];
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = data[rowIndex][colIndex] + other;
      }
    }
    return new Matrix(newData);
  }
  
  // TODO: addAssign function which doesn't return something but instead changes the values of the matrix
  public Matrix add(Matrix other) {
    if (rows() != other.rows()) {
      throw new RuntimeException("Rows doesn't match with others " + rows() + ", " + other.rows());
    } else if (cols() != other.cols()) {
      throw new RuntimeException("Column doesn't match with others " + cols() + ", " + other.cols());
    }
    double[][] newData = new double[rows()][cols()];
    
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = data[rowIndex][colIndex] + other.data[rowIndex][colIndex];
      }
    }
    return new Matrix(newData);
  }
  
  public Matrix inverse() {
    double[][] newData = new double[rows()][cols()];
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = -data[rowIndex][colIndex];
      }
    }
    return new Matrix(newData);
  }
  
  public Matrix subtract(double other) {
    double[][] newData = new double[rows()][cols()];
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = data[rowIndex][colIndex] - other;
      }
    }
    return new Matrix(newData);
  }
  
  public Matrix subtractReverse(double other) {
    double[][] newData = new double[rows()][cols()];
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = other - data[rowIndex][colIndex];
      }
    }
    return new Matrix(newData);
  }
  
  public Matrix subtract(Matrix other) {
    if (rows() != other.rows()) {
      throw new RuntimeException("Rows doesn't match with others " + rows() + ", " + other.rows());
    } else if (cols() != other.cols()) {
      throw new RuntimeException("Column doesn't match with others " + cols() + ", " + other.cols());
    }
    
    double[][] newData = new double[rows()][cols()];
    
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = data[rowIndex][colIndex] - other.data[rowIndex][colIndex];
      }
    }
    return new Matrix(newData);
  }
  
  public Matrix multiplyScalar(double other) {
    double[][] newData = new double[rows()][cols()];
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = data[rowIndex][colIndex] * other;
      }
    }
    return new Matrix(newData);
  }
  
  public static Matrix divideScalar(double value, Matrix matrix) {
    double[][] newData = new double[matrix.rows()][matrix.cols()];
    for (int rowIndex = 0; rowIndex < matrix.rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < matrix.cols(); colIndex++) {
        newData[rowIndex][colIndex] = value / matrix.data[rowIndex][colIndex];
      }
    }
    return new Matrix(newData);
  }
  
  public static Matrix subtractScalar(double value, Matrix matrix) {
    double[][] newData = new double[matrix.rows()][matrix.cols()];
    for (int rowIndex = 0; rowIndex < matrix.rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < matrix.cols(); colIndex++) {
        newData[rowIndex][colIndex] = value - matrix.data[rowIndex][colIndex];
      }
    }
    return new Matrix(newData);
  }
  
  // represents the numpy.dot(a, b) function of array and matrix
  public Matrix multiplyDot(Matrix other) {
    if (cols() != other.rows()) {
      throw new RuntimeException("Columns doesn't match rows " + cols() + ", " + other.rows());
    }
    double[][] newData = new double[rows()][other.cols()];
    
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < other.cols(); colIndex++) {
        double sum = 0;
        for (int otherColIndex = 0; otherColIndex < cols(); otherColIndex++) {
          sum += data[rowIndex][otherColIndex] * other.data[otherColIndex][colIndex];
        }
        newData[rowIndex][colIndex] = sum;
      }
    }
    
    return new Matrix(newData);
  }
  
  public Matrix multiplyHadamard(Matrix other) {
    double[][] newData = new double[rows()][cols()];
    // hadamard product
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = data[rowIndex][colIndex] * other.data[rowIndex][colIndex];
      }
    }
    
    return new Matrix(newData);
  }
  
  public Matrix divide(double other) {
    double[][] newData = new double[rows()][cols()];
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < cols(); colIndex++) {
        newData[rowIndex][colIndex] = data[rowIndex][colIndex] / other;
      }
    }
    return new Matrix(newData);
  }
  
  public Matrix divide(Matrix other) {
    if (cols() != other.rows()) {
      throw new RuntimeException("Columns doesn't match rows " + cols() + ", " + other.rows());
    }
    
    double[][] newData = new double[rows()][other.cols()];
    
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      for (int colIndex = 0; colIndex < other.cols(); colIndex++) {
        double sum = 0;
        for (int otherColIndex = 0; otherColIndex < cols(); otherColIndex++) {
          sum += data[rowIndex][otherColIndex] / other.data[otherColIndex][colIndex];
        }
        newData[rowIndex][colIndex] = sum;
      }
    }
    
    return new Matrix(newData);
  }
  
  public String dimension() {
    return "(" + rows() + ", " + cols() + ")";
  }
  
  @Override
  public String toString() {
    String output = "matrix(";
    
    for (int rowIndex = 0; rowIndex < rows(); rowIndex++) {
      output += "[";
      for (int colIndex = 0; colIndex < data[rowIndex].length; colIndex++) {
        output += data[rowIndex][colIndex] + ", ";
      }
      output = output.substring(0, output.length() - 2);
      output += "], ";
    }
    output = output.substring(0, output.length() - 2);
    
    return output + ")";
  }
}
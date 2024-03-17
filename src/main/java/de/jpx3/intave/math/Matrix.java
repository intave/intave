package de.jpx3.intave.math;

import java.util.Arrays;

public class Matrix {
  private double[][] elements;
  private final int rows;
  private final int columns;
  private boolean transposed;
  private boolean memoryShared = false;

  public Matrix(int rows, int columns) {
    this.elements = new double[rows][columns];
    this.rows = rows;
    this.columns = columns;
    this.transposed = false;
  }

  public Matrix(double[][] elements) {
    this.elements = elements;
    this.rows = elements.length;
    this.columns = elements[0].length;
    this.transposed = false;
    this.memoryShared = true;
  }

  public static Matrix eye(int size) {
    Matrix eye = new Matrix(size, size);
    for (int i = 0; i < size; i++) {
      eye.set(i, i, 1.0D);
    }
    return eye;
  }

  public static Matrix identity(int size) {
    return eye(size);
  }

  public static Matrix zeros(int rows, int columns) {
    return new Matrix(rows, columns);
  }

  public static Matrix column(double[] column) {
    Matrix matrix = new Matrix(column.length, 1);
    for (int i = 0; i < column.length; i++) {
      matrix.set(i, 0, column[i]);
    }
    return matrix;
  }

  public static Matrix emptyColumn(int rows) {
    return new Matrix(rows, 1);
  }

  public static Matrix row(double[] row) {
    Matrix matrix = new Matrix(1, row.length);
    for (int i = 0; i < row.length; i++) {
      matrix.set(0, i, row[i]);
    }
    return matrix;
  }

  public static Matrix emptyRow(int columns) {
    return new Matrix(1, columns);
  }

  public void set(int row, int column, double value) {
    if (memoryShared) {
      elements = Arrays.copyOf(elements, elements.length);
      memoryShared = false;
    }
    if (transposed) {
      elements[column][row] = value;
    } else {
      elements[row][column] = value;
    }
  }

  public double get(int row, int column) {
    if (transposed) {
      return elements[column][row];
    }
    return elements[row][column];
  }

  public int rows() {
    return transposed ? columns : rows;
  }

  public int columns() {
    return transposed ? rows : columns;
  }

  public Matrix transposed() {
    Matrix matrix = new Matrix(elements);
    matrix.transposed = !transposed;
    return matrix;
  }

  public boolean isTransposed() {
    return transposed;
  }

  public Matrix add(Matrix matrix) {
    if (rows() != matrix.rows() || columns() != matrix.columns()) {
      throw new IllegalArgumentException("Matrix dimensions must be equal");
    }
    Matrix result = new Matrix(rows(), columns());
    for (int i = 0; i < rows(); i++) {
      for (int j = 0; j < columns(); j++) {
        result.set(i, j, get(i, j) + matrix.get(i, j));
      }
    }
    return result;
  }

  public Matrix subtract(Matrix matrix) {
    if (rows() != matrix.rows() || columns() != matrix.columns()) {
      throw new IllegalArgumentException("Matrix dimensions must be equal");
    }
    Matrix result = new Matrix(rows(), columns());
    for (int i = 0; i < rows(); i++) {
      for (int j = 0; j < columns(); j++) {
        result.set(i, j, get(i, j) - matrix.get(i, j));
      }
    }
    return result;
  }

  public Matrix multiply(Matrix matrix) {
    if (columns() != matrix.rows()) {
      throw new IllegalArgumentException("Matrix dimensions must be equal");
    }
    Matrix result = new Matrix(rows(), matrix.columns());
    for (int i = 0; i < rows(); i++) {
      for (int j = 0; j < matrix.columns(); j++) {
        double sum = 0.0D;
        for (int k = 0; k < columns(); k++) {
          sum += get(i, k) * matrix.get(k, j);
        }
        result.set(i, j, sum);
      }
    }
    return result;
  }

  public Matrix multiply(double scalar) {
    Matrix result = new Matrix(rows(), columns());
    for (int i = 0; i < rows(); i++) {
      for (int j = 0; j < columns(); j++) {
        result.set(i, j, get(i, j) * scalar);
      }
    }
    return result;
  }

  public Matrix pinv() {
    Matrix transpose = transposed();
    return (transpose.multiply(this)).inverse().multiply(transpose);
  }

  public Matrix submatrix(int row, int column) {
    if (row < 0 || row >= rows() || column < 0 || column >= columns()) {
      throw new IllegalArgumentException("Row or column out of bounds");
    }
    Matrix result = new Matrix(rows() - 1, columns() - 1);
    for (int i = 0; i < rows(); i++) {
      for (int j = 0; j < columns(); j++) {
        if (i != row && j != column) {
          int r = i < row ? i : i - 1;
          int c = j < column ? j : j - 1;
          result.set(r, c, get(i, j));
        }
      }
    }
    return result;
  }

  public Matrix inverse() {
    if (rows() != columns()) {
      throw new IllegalArgumentException("Matrix must be square: " + rows() + "x" + columns());
    }
    double determinant = determinant();
    if (determinant == 0.0D) {
      throw new IllegalArgumentException("Matrix is singular");
    }
    return adjoint().multiply(1.0D / determinant);
  }

  public Matrix adjoint() {
    Matrix result = new Matrix(rows(), columns());
    for (int i = 0; i < rows(); i++) {
      for (int j = 0; j < columns(); j++) {
        result.set(i, j, Math.pow(-1, i + j) * submatrix(j, i).determinant());
      }
    }
    return result;
  }

  public double determinant() {
    if (rows() != columns()) {
      throw new IllegalArgumentException("Matrix must be square");
    }
    if (rows() == 1) {
      return get(0, 0);
    }
    if (rows() == 2) {
      return get(0, 0) * get(1, 1) - get(0, 1) * get(1, 0);
    }
    double determinant = 0.0D;
    for (int j = 0; j < columns(); j++) {
      determinant += Math.pow(-1, j) * get(0, j) * submatrix(0, j).determinant();
    }
    return determinant;
  }

  public double trace() {
    if (rows() != columns()) {
      throw new IllegalArgumentException("Matrix must be square");
    }
    double trace = 0.0D;
    for (int i = 0; i < rows(); i++) {
      trace += get(i, i);
    }
    return trace;
  }

  public double norm() {
    return norm(2.0D);
  }

  public double norm(double p) {
    if (p < 1.0D) {
      throw new IllegalArgumentException("p must be greater than or equal to 1");
    }
    double norm = 0.0D;
    for (int i = 0; i < rows(); i++) {
      for (int j = 0; j < columns(); j++) {
        norm += Math.pow(Math.abs(get(i, j)), p);
      }
    }
    return Math.pow(norm, 1.0D / p);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < rows(); i++) {
      for (int j = 0; j < columns(); j++) {
        builder.append(get(i, j));
        if (j < columns() - 1) {
          builder.append(" ");
        }
      }
      if (i < rows() - 1) {
        builder.append("\n");
      }
    }
    return builder.toString();
  }
}

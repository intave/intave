package de.jpx3.intave.block.shape.voxel;

import java.util.List;

public interface IndexList {
  int size();
  double get(int index);

  default double[] toDoubleArray() {
    double[] array = new double[size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = get(i);
    }
    return array;
  }

  static IndexList of(double[] coords) {
    return new IndexList() {
      @Override
      public int size() {
        return coords.length;
      }

      @Override
      public double get(int index) {
        return coords[index];
      }
    };
  }

  static IndexList of(double[] coords, int start, int end) {
    return new IndexList() {
      @Override
      public int size() {
        return end - start;
      }

      @Override
      public double get(int index) {
        return coords[start + index];
      }
    };
  }

  static IndexList of(double value) {
    return new IndexList() {
      @Override
      public int size() {
        return 1;
      }

      @Override
      public double get(int index) {
        return value;
      }

      @Override
      public double[] toDoubleArray() {
        return new double[]{value};
      }
    };
  }

  static IndexList of(List<? extends Integer> list) {
    return new IndexList() {
      @Override
      public int size() {
        return list.size();
      }

      @Override
      public double get(int index) {
        return list.get(index);
      }
    };
  }

  IndexList EMPTY = new IndexList() {
    @Override
    public int size() {
      return 0;
    }

    @Override
    public double get(int index) {
      throw new IndexOutOfBoundsException();
    }

    @Override
    public double[] toDoubleArray() {
      return new double[0];
    }
  };

  static IndexList empty() {
    return EMPTY;
  }
}

/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.share;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public class CubeIterator implements Iterable<MutableBlockPosition> {
  public static final int TYPE_INSIDE = 0;
  public static final int TYPE_FACE = 1;
  public static final int TYPE_EDGE = 2;
  public static final int TYPE_CORNER = 3;
  private final int originX;
  private final int originY;
  private final int originZ;
  private final int width;
  private final int height;
  private final int depth;
  private final int end;
  private int index;
  private int x;
  private int y;
  private int z;

  public CubeIterator(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
    this.originX = fromX;
    this.originY = fromY;
    this.originZ = fromZ;
    this.width = toX - fromX + 1;
    this.height = toY - fromY + 1;
    this.depth = toZ - fromZ + 1;
    this.end = this.width * this.height * this.depth;
  }

  public boolean advance() {
    if (this.index == this.end) {
      return false;
    } else {
      this.x = this.index % this.width;
      int dividedIndex = this.index / this.width;
      this.y = dividedIndex % this.height;
      this.z = dividedIndex / this.height;
      ++this.index;
      return true;
    }
  }

  public int nextX() {
    return this.originX + this.x;
  }

  public int nextY() {
    return this.originY + this.y;
  }

  public int nextZ() {
    return this.originZ + this.z;
  }

  public int nextType() {
    int onEdge = 0;
    if (this.x == 0 || this.x == this.width - 1) {
      ++onEdge;
    }
    if (this.y == 0 || this.y == this.height - 1) {
      ++onEdge;
    }
    if (this.z == 0 || this.z == this.depth - 1) {
      ++onEdge;
    }
    return onEdge;
  }

  @Override
  public @NotNull Iterator<MutableBlockPosition> iterator() {
    return new Iterator<MutableBlockPosition>() {
      private final MutableBlockPosition pos = new MutableBlockPosition();

      @Override
      public boolean hasNext() {
        return CubeIterator.this.index < CubeIterator.this.end;
      }

      @Override
      public MutableBlockPosition next() {
        CubeIterator.this.advance();
        pos.setX(CubeIterator.this.nextX());
        pos.setY(CubeIterator.this.nextY());
        pos.setZ(CubeIterator.this.nextZ());
        return this.pos;
      }
    };
  }
}

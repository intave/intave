package de.jpx3.intave.world.wrapper.link;

import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.world.wrapper.WrappedBlockPosition;
import de.jpx3.intave.world.wrapper.WrappedVector;

public final class WrapperLinkage {
  private static ClassLinker<WrappedAxisAlignedBB> boundingBoxLinker;
  private static ClassLinker<WrappedBlockPosition> blockPositionLinker;
  private static ClassLinker<WrappedVector> vec3DLinker;

  public static void setup() {
    boundingBoxLinker = BoundingBoxLinkage.resolveBoundingBoxLinker();
    blockPositionLinker = BlockPositionLinkage.resolveBlockPositionLinker();
    vec3DLinker = Vec3DLinkage.resolveVec3DLinker();
  }

  public static WrappedAxisAlignedBB boundingBoxOf(Object obj) {
    return boundingBoxLinker.link(obj);
  }

  public static WrappedBlockPosition blockPositionOf(Object obj) {
    return blockPositionLinker.link(obj);
  }

  public static WrappedVector vectorOf(Object obj) {
    return vec3DLinker.link(obj);
  }
}
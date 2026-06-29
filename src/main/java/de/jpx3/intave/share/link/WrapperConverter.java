package de.jpx3.intave.share.link;

import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.RawVector3d;

public final class WrapperConverter {
  private static ClassConverter<BoundingBox> boundingBoxLinker;
  private static ClassConverter<BlockPosition> blockPositionLinker;
  private static ClassConverter<RawVector3d> vec3DLinker;

  public static void setup() {
    boundingBoxLinker = BoundingBoxLinkage.resolveBoundingBoxConverter();
    blockPositionLinker = BlockPositionLinkage.resolveBlockPositionConverter();
    vec3DLinker = Vec3DLinkage.resolveVec3DConverter();
  }

  public static BoundingBox boundingBoxFromAABB(Object obj) {
    return boundingBoxLinker.convert(obj);
  }

  public static BlockPosition blockPositionFromNativeBlockPosition(Object obj) {
    return blockPositionLinker.convert(obj);
  }

  public static RawVector3d vectorFromVec3D(Object obj) {
    return vec3DLinker.convert(obj);
  }
}
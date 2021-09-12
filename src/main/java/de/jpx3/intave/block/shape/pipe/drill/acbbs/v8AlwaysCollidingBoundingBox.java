package de.jpx3.intave.block.shape.pipe.drill.acbbs;

import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.clazz.rewrite.PatchyTranslateParameters;
import net.minecraft.server.v1_8_R3.AxisAlignedBB;

@PatchyAutoTranslation
public final class v8AlwaysCollidingBoundingBox extends AxisAlignedBB {
  @PatchyAutoTranslation
  public v8AlwaysCollidingBoundingBox() {
    super(0,0,0,1,1,1);
  }

  @Override
  @PatchyAutoTranslation
  @PatchyTranslateParameters
  public boolean b(AxisAlignedBB axisAlignedBB) {
    return true;
  }
}
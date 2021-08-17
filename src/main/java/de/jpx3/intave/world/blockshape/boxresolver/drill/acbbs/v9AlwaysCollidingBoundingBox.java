package de.jpx3.intave.world.blockshape.boxresolver.drill.acbbs;

import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.reflect.patchy.annotate.PatchyTranslateParameters;
import net.minecraft.server.v1_9_R2.AxisAlignedBB;

@PatchyAutoTranslation
public final class v9AlwaysCollidingBoundingBox extends AxisAlignedBB {
  @PatchyAutoTranslation
  public v9AlwaysCollidingBoundingBox() {
    super(0,0,0,1,1,1);
  }

  @Override
  @PatchyAutoTranslation
  @PatchyTranslateParameters
  public boolean b(AxisAlignedBB axisAlignedBB) {
    return true;
  }
}
package de.jpx3.intave.world.collision.resolver.ac;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.patchy.annotate.PatchyTranslateParameters;
import net.minecraft.server.v1_12_R1.AxisAlignedBB;

@PatchyAutoTranslation
public final class v12AlwaysCollidingBoundingBox extends AxisAlignedBB {
  @PatchyAutoTranslation
  public v12AlwaysCollidingBoundingBox() {
    super(0,0,0,1,1,1);
  }

  @Override
  @PatchyAutoTranslation
  @PatchyTranslateParameters
  public boolean c(AxisAlignedBB axisAlignedBB) {
    return true;
  }
}
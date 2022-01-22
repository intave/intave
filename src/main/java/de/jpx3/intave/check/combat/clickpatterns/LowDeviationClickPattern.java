package de.jpx3.intave.check.combat.clickpatterns;

import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

public final class LowDeviationClickPattern extends MetaCheckPart<ClickPatterns, LowDeviationClickPattern.LowDeviationMeta> {
  public LowDeviationClickPattern(ClickPatterns parentCheck) {
    super(parentCheck, LowDeviationMeta.class);
  }

  public static class LowDeviationMeta extends CheckCustomMetadata {
    
  }
}

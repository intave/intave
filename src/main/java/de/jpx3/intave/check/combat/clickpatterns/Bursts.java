package de.jpx3.intave.check.combat.clickpatterns;

import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.ClickPatterns;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

public final class Bursts extends TickAlignedHistoryBlueprint<Bursts.BurstMeta> {
  public Bursts(ClickPatterns parentCheck) {
    super(parentCheck, BurstMeta.class);
  }

  public static class BurstMeta extends TickAlignedMeta {

  }
}


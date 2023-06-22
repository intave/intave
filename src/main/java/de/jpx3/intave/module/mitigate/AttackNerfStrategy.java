package de.jpx3.intave.module.mitigate;

import java.util.HashMap;
import java.util.Map;

public enum AttackNerfStrategy {
  CANCEL("cancel"),
  CANCEL_FIRST_HIT("cancel/first"),
  DMG_HIGH("dmg/high"),
  DMG_MEDIUM("dmg/medium"),
  DMG_LIGHT("dmg/light"),
  DMG_ARMOR("dmg/armor"),
  DMG_ARMOR_INEFFECTIVE("dmg/armor/ineffective"),
  @Deprecated
  HT_MEDIUM("ht/medium"),
  HT_LIGHT("ht/light"),
  GARBAGE_HITS("ht/jitter"),
  BLOCKING("dmg/blocking"),
  CRITICALS("dmg/criticals"),
  BURN_LONGER("burn-longer"),
  WALK_SLOW("walk-slower")

  ;

  private final String typeName;

  AttackNerfStrategy(String name) {
    this.typeName = name;
  }

  public String typeName() {
    return typeName;
  }

  private static final Map<String, AttackNerfStrategy> BY_NAME = new HashMap<>();

  static {
    for (AttackNerfStrategy strategy : values()) {
      BY_NAME.put(strategy.typeName, strategy);
    }
  }

  public static AttackNerfStrategy byName(String name) {
    return BY_NAME.get(name);
  }
}
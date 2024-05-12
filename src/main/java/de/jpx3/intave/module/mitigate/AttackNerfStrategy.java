package de.jpx3.intave.module.mitigate;

import java.util.HashMap;
import java.util.Map;

public enum AttackNerfStrategy {
  CANCEL("cancel", "full block of all attacks"),
  CANCEL_FIRST_HIT("cancel/first"),
  RECEIVE_MORE_KNOCKBACK("kb/receive-more", "receive more knockback"),
  APPLY_LESS_KNOCKBACK("kb/apply-less", "apply less knockback"),
  DMG_HIGH("dmg/high", "moderate damage reduction"),
  DMG_MEDIUM("dmg/medium", "medium damage reduction"),
  DMG_LIGHT("dmg/light", "slight damage reduction"),
  DMG_ARMOR("dmg/armor", "armor protection reduction"),
  DMG_ARMOR_INEFFECTIVE("dmg/armor/ineffective", "enemy armor buff"),
  @Deprecated
  HT_MEDIUM("ht/medium"),
  HT_LIGHT("ht/light"),
  HT_SPOOF("ht/spoof"),
  GARBAGE_HITS("ht/jitter"),
  BLOCKING("dmg/blocking", "removal of blocking reduction"),
  CRITICALS("dmg/criticals", "downgrade of critical hits"),
  BURN_LONGER("burn-longer"),
  WALK_SLOW("walk-slower")

  ;

  private final String typeName;
  private final boolean showToUsers;
  private final String description;

  AttackNerfStrategy(String name) {
    this.typeName = name;
    this.showToUsers = false;
    this.description = null;
  }

  AttackNerfStrategy(String name, String description) {
    this.typeName = name;
    this.showToUsers = true;
    this.description = description;
  }

  public String typeName() {
    return typeName;
  }

  public boolean showToUsers() {
    return showToUsers;
  }

  public String description() {
    return description;
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
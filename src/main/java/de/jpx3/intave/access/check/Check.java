package de.jpx3.intave.access.check;

/**
 * An alphabetically-sorted enumerator of the names of all checks in Intave
 */
public enum Check {
  ATTACK_RAYTRACE("AttackRaytrace"),
  BREAK_SPEED_LIMITER("BreakSpeedLimiter"),
  CLICK_SPEED_LIMITER("ClickSpeedLimiter"),
  HEURISTICS("Heuristics"),
  INTERACTION_RAYTRACE("InteractionRaytrace"),
  INVENTORY_CLICK_ANALYSIS("InventoryClickAnalysis"),
  PHYSICS("Physics"),
  PROTOCOL_SCANNER("ProtocolScanner"),
  PLACEMENT_ANALYSIS("PlacementAnalysis"),
  TIMER("Timer"),

  ;

  private final String typeName;

  Check(String name) {
    this.typeName = name;
  }

  public static Check fromString(String name) {
    for (Check value : values()) {
      if (value.typeName.equalsIgnoreCase(name)) {
        return value;
      }
    }
    return null;
  }

  public String typeName() {
    return typeName;
  }
}

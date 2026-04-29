package de.jpx3.intave.check.combat.heuristics;

public enum HeuristicsClassicType {
  ATTACK_ACCURACY("attack-accuracy"),
  ROTATION_ACCURACY("rotation-accuracy"),
  ROTATION_EXACT("rotation-exact"),
  ROTATION_SNAP("rotation-snap"),
  ROTATION_MODULO_RESET("rotation-reset"),
  ROTATION_SENSITIVITY("rotation-sensitivity"),
  ATTACK_REQUIRED("attack-required"),
  PRE_ATTACK("pre-attack"),
  INVENTORY_ROTATIONS("inventory-rotations"),;

  private final String configurationName;

  HeuristicsClassicType(String configurationName) {
    this.configurationName = configurationName;
  }

  public String configurationName() {
    return configurationName;
  }

  public String verboseName() {
    return configurationName;
  }
}

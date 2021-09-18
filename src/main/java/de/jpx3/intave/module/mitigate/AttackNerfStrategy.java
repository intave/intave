package de.jpx3.intave.module.mitigate;

public enum AttackNerfStrategy {
  CANCEL("cancel"),
  CANCEL_FIRST_HIT("cancel/first"),
  DMG_MEDIUM("dmg/medium"),
  DMG_LIGHT("dmg/light"),
  HT_MEDIUM("ht/medium"),
  HT_LIGHT("ht/light"),
  GARBAGE_HITS("garbage-hits"),
  BLOCKING("blocking");

  private final String typeName;

  AttackNerfStrategy(String name) {
    this.typeName = name;
  }

  public String typeName() {
    return typeName;
  }
}
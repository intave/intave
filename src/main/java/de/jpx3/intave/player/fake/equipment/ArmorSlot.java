package de.jpx3.intave.player.fake.equipment;

public enum ArmorSlot {
  HELMET(5),
  CHESTPLATE(4),
  LEGGINGS(3),
  BOOTS(2);

  private final int slotId;

  ArmorSlot(int slotId) {
    this.slotId = slotId;
  }

  public int slotId() {
    return this.slotId;
  }
}

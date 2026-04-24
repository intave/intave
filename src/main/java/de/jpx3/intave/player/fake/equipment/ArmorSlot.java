package de.jpx3.intave.player.fake.equipment;

import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;

public enum ArmorSlot {
  HELMET(EquipmentSlot.HELMET, 4),
  CHESTPLATE(EquipmentSlot.CHEST_PLATE, 3),
  LEGGINGS(EquipmentSlot.LEGGINGS, 2),
  BOOTS(EquipmentSlot.BOOTS, 1);

  private final EquipmentSlot itemSlot;
  private final int slotId;

  ArmorSlot(EquipmentSlot itemSlot, int slotId) {
    this.itemSlot = itemSlot;
    this.slotId = slotId;
  }

  public EquipmentSlot itemSlot() {
    return this.itemSlot;
  }

  public int slotId() {
    return slotId;
  }
}

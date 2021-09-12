package de.jpx3.intave.player.fake.action;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.player.fake.equipment.ArmorPiece;
import de.jpx3.intave.player.fake.equipment.Equipment;
import de.jpx3.intave.player.fake.equipment.EquipmentFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class EquipmentArmorAction extends Action {
  public EquipmentArmorAction(Player player, FakePlayer fakePlayer) {
    super(Probability.LOW, player, fakePlayer);
  }

  @Override
  public void perform() {
    Equipment equipment = EquipmentFactory.randomEquipment();
    List<ArmorPiece> armorPieceList = equipment.armorPieces();
    for (ArmorPiece armorPiece : armorPieceList) {
      Material armorMaterial = armorPiece.material();
      if (armorMaterial == Material.AIR) {
        continue;
      }
      int slotId = armorPiece.type().slotId();
      sendEquipment(slotId - 1, armorMaterial);
    }
  }

  private final static boolean HAS_OFF_HAND = MinecraftVersions.VER1_9_0.atOrAbove();

  private void sendEquipment(
    int slot,
    Material material
  ) {
    PacketContainer packet = create(PacketType.Play.Server.ENTITY_EQUIPMENT);
    packet.getIntegers().writeSafely(0, this.fakePlayer.identifier());
    if (HAS_OFF_HAND) {
      EnumWrappers.ItemSlot itemSlot;
      switch (slot) {
        case 0:
          itemSlot = EnumWrappers.ItemSlot.HEAD;
          break;
        case 1:
          itemSlot = EnumWrappers.ItemSlot.CHEST;
          break;
        case 2:
          itemSlot = EnumWrappers.ItemSlot.LEGS;
          break;
        case 3:
          itemSlot = EnumWrappers.ItemSlot.FEET;
          break;
        default:
          return;
      }
      packet.getItemSlots().write(0, itemSlot);
    } else {
      packet.getModifier().write(1, slot);
    }
    packet.getItemModifier().writeSafely(0, new ItemStack(material));
    send(packet);
  }
}
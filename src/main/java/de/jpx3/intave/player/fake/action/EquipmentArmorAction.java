package de.jpx3.intave.player.fake.action;

import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.player.fake.equipment.ArmorPiece;
import de.jpx3.intave.player.fake.equipment.ArmorSlot;
import de.jpx3.intave.player.fake.equipment.Equipment;
import de.jpx3.intave.player.fake.equipment.EquipmentFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import java.util.Collections;
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
      if (armorMaterial != Material.AIR) {
        ArmorSlot type = armorPiece.type();
        sendEquipment(type, armorMaterial);
      }
    }
  }

  private void sendEquipment(ArmorSlot slot, Material material) {
    ItemStack itemStack = new ItemStack(material);
    EquipmentSlot equipmentSlot = slot.itemSlot();
    send(new WrapperPlayServerEntityEquipment(
      this.fakePlayer.identifier(),
      Collections.singletonList(new com.github.retrooper.packetevents.protocol.player.Equipment(
        equipmentSlot,
        SpigotConversionUtil.fromBukkitItemStack(itemStack)
      ))
    ));
  }
}

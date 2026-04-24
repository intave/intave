package de.jpx3.intave.player.fake.action;

import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.player.fake.equipment.Equipment;
import de.jpx3.intave.player.fake.equipment.EquipmentFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

public final class EquipmentHeldItemAction extends Action {
  public EquipmentHeldItemAction(Player player, FakePlayer fakePlayer) {
    super(Probability.MEDIUM, player, fakePlayer);
  }

  @Override
  public void perform() {
    Equipment equipment = EquipmentFactory.randomEquipment();
    Material heldItem = equipment.heldItem();
    if (heldItem != Material.AIR) {
      updateHeldItem(heldItem);
    }
  }

  private void updateHeldItem(Material material) {
    ItemStack itemStack = new ItemStack(material);
    EquipmentSlot hand = ThreadLocalRandom.current().nextInt(0, 10) == 5
      ? EquipmentSlot.OFF_HAND
      : EquipmentSlot.MAIN_HAND;
    send(new WrapperPlayServerEntityEquipment(
      this.fakePlayer.identifier(),
      Collections.singletonList(new com.github.retrooper.packetevents.protocol.player.Equipment(
        hand,
        SpigotConversionUtil.fromBukkitItemStack(itemStack)
      ))
    ));
  }
}

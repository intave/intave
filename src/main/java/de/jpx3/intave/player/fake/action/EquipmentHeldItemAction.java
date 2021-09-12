package de.jpx3.intave.player.fake.action;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.player.fake.equipment.Equipment;
import de.jpx3.intave.player.fake.equipment.EquipmentFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

  private final static boolean HAS_OFF_HAND = MinecraftVersions.VER1_9_0.atOrAbove();

  private void updateHeldItem(Material material) {
    PacketContainer packet = create(PacketType.Play.Server.ENTITY_EQUIPMENT);
    packet.getIntegers().write(0, this.fakePlayer.identifier());
    if (HAS_OFF_HAND) {
      EnumWrappers.ItemSlot hand = ThreadLocalRandom.current().nextInt(0, 10) == 5
        ? EnumWrappers.ItemSlot.OFFHAND
        : EnumWrappers.ItemSlot.MAINHAND;
      packet.getItemSlots().write(0, hand);
    } else {
      packet.getModifier().write(1, 0);
    }
    packet.getItemModifier().write(0, new ItemStack(material));
    send(packet);
  }
}
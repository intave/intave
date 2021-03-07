package de.jpx3.intave.event.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.detect.checks.combat.Heuristics;
import de.jpx3.intave.event.bukkit.BukkitEventSubscription;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketDescriptor;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.event.packet.Sender;
import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaAttackData;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AttackDispatcher implements EventProcessor {
  public static boolean REDUCING_DISABLED;

  public AttackDispatcher(IntavePlugin plugin) {
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
    plugin.eventLinker().registerEventsIn(this);
    REDUCING_DISABLED = plugin.checkService().searchCheck(Heuristics.class).configuration().settings().boolBy("disable-reducing", true);

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      disableReducing(onlinePlayer);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.CLIENT, packetName = "USE_ENTITY")
    }
  )
  public void receiveUseEntity(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.isDead()) {
      event.setCancelled(true);
      return;
    }
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaAttackData attackData = meta.attackData();
    UserMetaMovementData movementData = meta.movementData();

    PacketContainer packet = event.getPacket();
    Integer entityId = packet.getIntegers().read(0);
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().read(0);

    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      attackData.setLastAttackedEntityID(entityId);
      if (playerAttack(entityId)) {
        movementData.pastPlayerAttackPhysics = 0;
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "RESPAWN")
    }
  )
  public void sentRespawn(PacketEvent event) {
    Player player = event.getPlayer();
    Synchronizer.synchronizeDelayed(() -> disableReducing(player), 4);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packets = {
      @PacketDescriptor(sender = Sender.SERVER, packetName = "SET_SLOT")
    }
  )
  public void filterSharpness(PacketEvent event) {
    if (!REDUCING_DISABLED) {
      return;
    }
    PacketContainer packet = event.getPacket();
    ItemStack item = packet.getItemModifier().read(0).clone();
    if (item.containsEnchantment(Enchantment.DAMAGE_ALL)) {
      int level = item.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
      item.removeEnchantment(Enchantment.DAMAGE_ALL);
      ItemMeta itemMeta = item.getItemMeta().clone();
      if (!itemMeta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
        List<String> lore = itemMeta.getLore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.getLore());
        lore.add(ChatColor.GRAY + "Sharpness " + toRoman(level));
        itemMeta.setLore(lore);
      }
      if (!itemMeta.hasEnchants()) {
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        itemMeta.addEnchant(Enchantment.DURABILITY, 0,true);
      }
      item.setItemMeta(itemMeta);
    }
    packet.getItemModifier().write(0, item);
  }

  private final static int[] ROMAN_STEPS = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
  private final static String[] ROMAN_LITERALS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

  private String toRoman(int number) {
    StringBuilder roman = new StringBuilder();
    for (int i = 0; i < ROMAN_STEPS.length; i++) {
      while (number >= ROMAN_STEPS[i]) {
        number -= ROMAN_STEPS[i];
        roman.append(ROMAN_LITERALS[i]);
      }
    }
    return roman.toString();
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    disableReducing(join.getPlayer());
  }

  private void disableReducing(Player player) {
    if (!REDUCING_DISABLED) {
      return;
    }
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.UPDATE_ATTRIBUTES);
    packet.getIntegers().write(0, player.getEntityId());
    WrappedAttribute wrappedAttribute = WrappedAttribute.newBuilder().packet(packet).attributeKey("generic.attackDamage").baseValue(0).modifiers(Collections.emptyList()).build();
    packet.getAttributeCollectionModifier().write(0, Collections.singletonList(wrappedAttribute));

    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private boolean playerAttack(Integer entityId) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (player.getEntityId() == entityId) {
        return true;
      }
    }
    return false;
  }
}
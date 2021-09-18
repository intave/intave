package de.jpx3.intave.module.dispatch;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedAttribute;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.refactoring.SplitMeUp;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import de.jpx3.intave.player.dmc.DamageController;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.RESPAWN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SET_SLOT;
import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BLOCKING;

@SplitMeUp
public final class AttackDispatcher extends Module {
  public static boolean REDUCING_DISABLED;

  @Override
  public void enable() {
    REDUCING_DISABLED = !MinecraftVersions.VER1_9_0.atOrAbove() &&
      plugin.checks().searchCheck(Heuristics.class).configuration().settings().boolBy("disable-reducing", true);

    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      disableReducing(onlinePlayer);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.LOW,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveUseEntity(PacketEvent event) {
    Player player = event.getPlayer();
    if (player.isDead()) {
      event.setCancelled(true);
      return;
    }
    User user = UserRepository.userOf(player);
    MetadataBundle meta = user.meta();
    AttackMetadata attackData = meta.attack();
    ConnectionMetadata connectionData = meta.connection();
    MovementMetadata movementData = meta.movement();

    PacketContainer packet = event.getPacket();
    Integer entityId = packet.getIntegers().read(0);
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }

    InventoryMetadata inventoryData = user.meta().inventory();
    ItemStack itemStack = inventoryData.heldItem();
    boolean knockbackEnchantment = itemStack != null && itemStack.containsEnchantment(Enchantment.KNOCKBACK);

    WrappedEntity entity = connectionData.entityBy(entityId);
    if (entity == null) {
      return;
    }

    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      attackData.setLastAttackedEntityID(entityId);
      if (entity.player) {
        movementData.pastPlayerAttackPhysics = 0;
        if (knockbackEnchantment) {
          movementData.physicsMotionX *= 0.6;
          movementData.physicsMotionZ *= 0.6;
        }
      }
      FakePlayer fakePlayer = attackData.fakePlayer();
      if (fakePlayer != null) {
        fakePlayer.onAttack();
        if (fakePlayer.identifier() == entityId) {
          Consumer<FakePlayer> attackSubscriber = fakePlayer.attackSubscriber();
          Vector location = fakePlayer.movement().location.toVector();
          Vector actualLocation = new Vector(
            attackData.fakePlayerLastReportedX,
            attackData.fakePlayerLastReportedY,
            attackData.fakePlayerLastReportedZ
          );
          if (location.distance(actualLocation) < 0.1) {
            attackSubscriber.accept(fakePlayer);
          }
        }
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      RESPAWN
    }
  )
  public void sentRespawn(PacketEvent event) {
    Player player = event.getPlayer();
    Synchronizer.synchronizeDelayed(() -> disableReducing(player), 4);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      SET_SLOT
    }
  )
  public void filterSharpness(PacketEvent event) {
    PacketContainer packet = event.getPacket();
    ItemStack item = packet.getItemModifier().read(0).clone();
    if (REDUCING_DISABLED) {
      if (item.containsEnchantment(Enchantment.DAMAGE_ALL)) {
        int level = item.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
        item.removeEnchantment(Enchantment.DAMAGE_ALL);
        ItemMeta itemMeta = item.getItemMeta().clone();
        if (!itemMeta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
          List<String> lore = itemMeta.getLore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.getLore());
          lore.add(ChatColor.GRAY + "Sharpness " + toRomanLiteral(level));
          itemMeta.setLore(lore);
        }
        if (!itemMeta.hasEnchants()) {
          itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
          itemMeta.addEnchant(Enchantment.DURABILITY, 0, true);
        }
        item.setItemMeta(itemMeta);
      }
    }
//    if (IntaveControl.GOMME_MODE) {
//      if (item.containsEnchantment(Enchantment.KNOCKBACK)) {
//        int level = item.getEnchantmentLevel(Enchantment.KNOCKBACK);
//        item.removeEnchantment(Enchantment.KNOCKBACK);
//        ItemMeta itemMeta = item.getItemMeta().clone();
//        if (!itemMeta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
//          List<String> lore = itemMeta.getLore() == null ? new ArrayList<>() : new ArrayList<>(itemMeta.getLore());
//          lore.add(ChatColor.GRAY + "Knockback " + toRoman(level));
//          itemMeta.setLore(lore);
//        }
//        if (!itemMeta.hasEnchants()) {
//          itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
//          itemMeta.addEnchant(Enchantment.DURABILITY, 0,true);
//        }
//        item.setItemMeta(itemMeta);
//      }
//    }
    packet.getItemModifier().write(0, item);
  }

  private final static int[] ROMAN_STEPS = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
  private final static String[] ROMAN_LITERALS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

  public static String toRomanLiteral(int number) {
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
  public void on(EntityDamageByEntityEvent event) {
    if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
      return;
    }
    Entity attacked = event.getEntity();
    if (!(attacked instanceof Player)) {
      return;
    }
    Player attackedPlayer = (Player) attacked;
    User user = UserRepository.userOf(attackedPlayer);
    user.meta().attack().noteExternalAttack();
    double blockingDamageAbsorption = event.getDamage(BLOCKING);
    if (blockingDamageAbsorption < 0 && !user.meta().inventory().handActive()) {
      DamageController.withNewDamageApplier(event, BLOCKING, current -> -0d);
    }
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
}
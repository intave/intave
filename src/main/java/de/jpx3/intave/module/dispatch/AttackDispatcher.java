package de.jpx3.intave.module.dispatch;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.player.DamageModify;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.*;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.RESPAWN;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.SET_SLOT;
import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BLOCKING;

public final class AttackDispatcher extends Module {
  public static boolean REDUCING_DISABLED;
  public static boolean COMBAT_SAMPLING = true;

  @Override
  public void enable() {
    REDUCING_DISABLED = !MinecraftVersions.VER1_9_0.atOrAbove() &&
      plugin.checks().searchCheck(Heuristics.class).configuration().settings().boolBy("disable-reducing", false);
    COMBAT_SAMPLING = plugin.checks().searchCheck(Heuristics.class).configuration().settings().boolBy("combat-sampling", true);
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
  public void receiveUseEntity(ProtocolPacketEvent event, WrapperPlayClientInteractEntity packet) {
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
    Integer entityId = packet.getEntityId();
    WrapperPlayClientInteractEntity.InteractAction action = packet.getAction();
    InventoryMetadata inventoryData = user.meta().inventory();
    ItemStack itemStack = inventoryData.heldItem();

    double f = user.meta().abilities().attributeValue("generic.attackDamage");
    double f1 = itemStack == null ? 0 : Math.max(itemStack.getEnchantmentLevel(Enchantment.DAMAGE_ARTHROPODS), itemStack.getEnchantmentLevel(Enchantment.DAMAGE_UNDEAD));
    double itemKnockback = itemStack == null ? 0 : itemStack.getEnchantmentLevel(Enchantment.KNOCKBACK);
    boolean isSprinting = movementData.isSprinting();

    Entity entity = connectionData.entityBy(entityId);
    if (entity == null) {
      return;
    }

    int ticks = (int) entity.pendingFeedbackPackets();
    connectionData.attackDelays.occurred(ticks);

    movementData.pastEntityUse = 0;
    if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
      if (attackData.attackPastTicks > 10) {
        attackData.attackPastTicks = 0;
      }
      attackData.setLastAttackedEntityID(entityId);
      // Sprinting will be set to zero after the first reduce in the tick, does not apply to knockback
      boolean limitedToOneAttack = itemKnockback == 0;
      if (entity.isPlayer && (f > 0 || f1 > 0) && (isSprinting || itemKnockback > 0)) {
        movementData.pastPlayerReduceAttackPhysics = 0;
        if (movementData.reduceTicks == 0 || !limitedToOneAttack) {
          movementData.reduceTicks++;
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
  public void sentRespawn(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    Synchronizer.synchronizeDelayed(() -> disableReducing(player), 4);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      SET_SLOT
    }
  )
  public void filterSharpness(ProtocolPacketEvent event, WrapperPlayServerSetSlot packet) {
    if (packet.getItem() == null) {
      return;
    }
    ItemStack item = SpigotConversionUtil.toBukkitItemStack(packet.getItem()).clone();
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
    packet.setItem(SpigotConversionUtil.fromBukkitItemStack(item));
    event.markForReEncode(true);
  }

  private static final int[] ROMAN_STEPS = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
  private static final String[] ROMAN_LITERALS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

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
    org.bukkit.entity.Entity attacked = event.getEntity();
    if (!(attacked instanceof Player)) {
      return;
    }
    Player attackedPlayer = (Player) attacked;
    User user = UserRepository.userOf(attackedPlayer);
    user.meta().attack().noteExternalAttack();
    double blockingDamageAbsorption = event.getDamage(BLOCKING);
    if (blockingDamageAbsorption < 0 && !user.meta().inventory().handActive()) {
      DamageModify.withNewDamageApplier(event, BLOCKING, current -> -0d);
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
    WrapperPlayServerUpdateAttributes.Property property = new WrapperPlayServerUpdateAttributes.Property(
      Attributes.ATTACK_DAMAGE,
      0,
      Collections.emptyList()
    );
    WrapperPlayServerUpdateAttributes packet = new WrapperPlayServerUpdateAttributes(
      player.getEntityId(),
      Collections.singletonList(property)
    );
    PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
  }
}

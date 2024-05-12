package de.jpx3.intave.user.meta;

import com.google.common.collect.Lists;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.mitigate.HurttimeModifier;
import de.jpx3.intave.player.DamageModify;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.*;

@Relocate
public final class PunishmentMetadata {
  public static final long DAMAGE_CANCEL_LIGHT_DURATION = 80_000;
  private static final long DAMAGE_CANCEL_MEDIUM_DURATION = 40_000;
  private static final long DAMAGE_CANCEL_HEAVY_DURATION = 5_000;
  private static final long BLOCKING_DAMAGE_CANCEL_DURATION = 15_000;

  private static final long DAMAGE_CANCEL_FIRST_HIT_DURATION = 60_000;
  private static final long ENTITY_HURT_TIME_CHANGE_DURATION = 5_000;

  private static final long NO_CRITICAL_HITS_DURATION = 120_000;
  private static final long GARBAGE_HITS_DURATION = 120_000;
  private static final long BURN_LONGER_DURATION = 120_000;

  private final Map<AttackNerfStrategy, AttackNerfer> attackNerfersMap = new HashMap<>();
  private final List<AttackNerfer> attackNerfers;

  public int damageTicksBefore = -1;
  public int appliedDamageTicks = -1;
  public long timeLastBlockCancel;
  public long timeLastSneakToggleCancel;

  public double velocityIncreaseTokens = 6;
  public long lastVelocityIncreaseReset = System.currentTimeMillis();

  public int lastLowVelocityApplyId = -1;
  public long lastSwing = 0;

  private final Map<Integer, Long> lastTimeValidHurttimeAttack = new ConcurrentHashMap<>();
  private long delay = 600;

  public PunishmentMetadata(Player player) {
    this.attackNerfers = Lists.newArrayList(
      new AttackNerfer(
        AttackNerfStrategy.CANCEL, DAMAGE_CANCEL_HEAVY_DURATION,
        event -> event.setCancelled(true)
      ),
      new AttackNerfer(
        AttackNerfStrategy.HT_SPOOF, 750,
        event -> event.setCancelled(true)
      ),
      new AttackNerfer(
        AttackNerfStrategy.CANCEL_FIRST_HIT, DAMAGE_CANCEL_FIRST_HIT_DURATION,
        event -> {
        }
      ),
      new AttackNerfer(AttackNerfStrategy.CRITICALS, NO_CRITICAL_HITS_DURATION, event -> {
        User target = UserRepository.userOf(player);
        if (target.meta().protocol().combatUpdate()) {
          return;
        }
        if (ThreadLocalRandom.current().nextDouble() > 0.5) {
          double attackDamage = DamageModify.attackDamageOf((Player) event.getDamager());
          ItemStack heldItem = UserRepository.userOf((Player) event.getDamager()).meta().inventory().heldItem();
          attackDamage += DamageModify.sharpnessDamageOf(heldItem);
          event.setDamage(BASE, Math.min(attackDamage, event.getDamage(BASE)));
          DamageModify.refreshModifiers(event);
        }
      }),
      new AttackNerfer(
        AttackNerfStrategy.DMG_HIGH, DAMAGE_CANCEL_HEAVY_DURATION,
        event -> {
          event.setDamage(BASE, event.getDamage(BASE) * 0.5);
          DamageModify.refreshModifiers(event);
        }
      ),
      new AttackNerfer(
        AttackNerfStrategy.RECEIVE_MORE_KNOCKBACK, DAMAGE_CANCEL_LIGHT_DURATION,
        event -> {}
      ),
      new AttackNerfer(
        AttackNerfStrategy.APPLY_LESS_KNOCKBACK, DAMAGE_CANCEL_LIGHT_DURATION,
        event -> {
          User target = UserRepository.userOf((Player) event.getDamager());
          target.meta().punishment().lastLowVelocityApplyId = event.getEntity().getEntityId();
        }
      ),
      new AttackNerfer(
        AttackNerfStrategy.DMG_MEDIUM, DAMAGE_CANCEL_MEDIUM_DURATION,
        event -> {
          boolean similarArmor = armorSimilarity(player, event.getEntity()) > 0.75;
          double modifier = Math.abs(ThreadLocalRandom.current().nextGaussian() * (similarArmor ? 0.05 : 0.175)) + 0.8;
          if (modifier > 1) {
            modifier = 1;
          }
          event.setDamage(BASE, event.getDamage(BASE) * modifier);
          DamageModify.refreshModifiers(event);
        }
      ),
      new AttackNerfer(
        AttackNerfStrategy.DMG_LIGHT, DAMAGE_CANCEL_LIGHT_DURATION,
        event -> {
          event.setDamage(BASE, event.getDamage(BASE) * (armorSimilarity(player, event.getEntity()) > 0.75 ? 1 : 0.95));
          DamageModify.refreshModifiers(event);
        }
      ),
//      new AttackNerfer(AttackNerfStrategy.HT_MEDIUM, DAMAGE_CANCEL_MEDIUM_DURATION, event -> {
//        // Perform hurt-time change
//        int ticks = -ThreadLocalRandom.current().nextInt(0, 3);
//        HurttimeModifier.applyHurtTimeChangeTo(player, (int) (DAMAGE_CANCEL_MEDIUM_DURATION / 50), ticks);
//        // Perform hurt-time change on entity
//        performEntityHurtTimeChange(event.getEntity());
//      }),
      new AttackNerfer(AttackNerfStrategy.HT_LIGHT, DAMAGE_CANCEL_LIGHT_DURATION, event -> {
        User target = UserRepository.userOf(player);
        if (target.meta().protocol().combatUpdate()) {
          return;
        }
        // Perform hurt-time change
        int ticks = -ThreadLocalRandom.current().nextInt(0, 1);
        HurttimeModifier.applyHurtTimeChangeTo(player, (int) (DAMAGE_CANCEL_LIGHT_DURATION / 50), ticks);
      }),
      new AttackNerfer(AttackNerfStrategy.BURN_LONGER, BURN_LONGER_DURATION, event -> {
      }),
      new AttackNerfer(AttackNerfStrategy.BLOCKING, BLOCKING_DAMAGE_CANCEL_DURATION, event -> {
        Entity damaged = event.getEntity();
        if (damaged instanceof Player) {
          User target = UserRepository.userOf((Player) damaged);
          ItemStack heldItem = target.meta().inventory().heldItem();
          ItemStack offHandItem = target.meta().inventory().offhandItem();
          if ((heldItem != null && heldItem.getType().name().toUpperCase().contains("SHIELD"))
            || (offHandItem != null && offHandItem.getType().name().toUpperCase().contains("SHIELD"))
            || target.meta().protocol().combatUpdate()
          ) {
            return;
          }
        }
        DamageModify.withNewDamageApplier(event, BLOCKING, current -> -0d);
      }, true),
      new AttackNerfer(AttackNerfStrategy.DMG_ARMOR, DAMAGE_CANCEL_LIGHT_DURATION, event -> {
        DamageModify.modifyDamageApplier(event, ARMOR, (damage, armor) -> {
          if (armor < -2) {
            double actualDamage = damage + armor; // armor is negative
            double armorBuff = actualDamage * 0.33; // is positive
            return armor - Math.min(armorBuff, 1);
          }
          return armor;
        });
      }),
      new AttackNerfer(AttackNerfStrategy.DMG_ARMOR_INEFFECTIVE, DAMAGE_CANCEL_MEDIUM_DURATION, event -> {
        DamageModify.modifyDamageApplier(event, ARMOR, (damage, armor) -> {
          if (armor < -2) {
            double actualDamage = damage + armor; // armor is negative
            double armorBuff = actualDamage * 0.33;
            return Math.min(armor + Math.max(armorBuff, 1), -1);
          }
          return armor;
        });
      }, true),
      new AttackNerfer(AttackNerfStrategy.GARBAGE_HITS, GARBAGE_HITS_DURATION, event -> {
        User target = UserRepository.userOf(player);
        if (target.meta().protocol().combatUpdate()) {
          return;
        }
        int entityId = event.getEntity().getEntityId();
        long lastValidAttack = System.currentTimeMillis() - lastTimeValidHurttimeAttack.computeIfAbsent(entityId, x -> 0L);
        if (lastValidAttack < delay) {
          event.setCancelled(true);
        } else {
          int random = ThreadLocalRandom.current().nextInt(-10, 10);
          if (random < 0) {
            delay = 550;
          } else if (random < 5) {
            delay = 600;
          }/* else if (random < 8) {
            delay = 650;
          }*/ else {
            delay = 650;
          }
          lastTimeValidHurttimeAttack.put(entityId, System.currentTimeMillis());
        }
      })
    );
    for (AttackNerfer attackNerfer : attackNerfers) {
      this.attackNerfersMap.put(attackNerfer.type, attackNerfer);
    }
  }

//  private void performEntityHurtTimeChange(Entity entity) {
//    if (!(entity instanceof Player)) {
//      return;
//    }
//    Player player = (Player) entity;
//    int increase = 2;
//    HurttimeModifier.applyHurtTimeChangeTo(player, (int) (ENTITY_HURT_TIME_CHANGE_DURATION / 50), increase);
//  }

  private double armorSimilarity(Entity alpha, Entity beta) {
    if (!(alpha instanceof Player) || !(beta instanceof Player)) {
      return 0;
    }
    ItemStack[] armorContents = ((Player) alpha).getEquipment().getArmorContents();
    ItemStack[] armorContents2 = ((Player) beta).getEquipment().getArmorContents();
    double similarity = 0;
    for (int i = 0; i < armorContents.length; i++) {
      ItemStack item = armorContents[i];
      ItemStack item2 = armorContents2[i];
      if (item == null || item2 == null) {
        if (item == item2) {
          similarity++;
        }
        continue;
      }
      if (item.getType() == item2.getType()) {
        similarity++;
      }
    }

    return similarity / 4;
  }

  public List<AttackNerfer> allNerfers() {
    return attackNerfers;
  }

  public List<AttackNerfer> activeNerfers() {
    return attackNerfers.stream().filter(AttackNerfer::active).collect(Collectors.toList());
  }

  public AttackNerfer nerferOfType(AttackNerfStrategy type) {
    return attackNerfersMap.get(type);
  }

  public static final class AttackNerfer {
    private final AttackNerfStrategy type;
    private final Consumer<EntityDamageByEntityEvent> executor;
    private final long duration;
    private final boolean inverseEvent;
    private int executed = 0;
    private int limit = -1;
    private boolean permanent = false;
    private long activated;
    private boolean show = true;

    public AttackNerfer(
      AttackNerfStrategy type,
      long duration,
      Consumer<EntityDamageByEntityEvent> executor
    ) {
      this(type, duration, executor, false);
    }

    public AttackNerfer(
      AttackNerfStrategy type,
      long duration,
      Consumer<EntityDamageByEntityEvent> executor,
      boolean inverseEvent
    ) {
      this.type = type;
      this.duration = duration;
      this.executor = executor;
      this.inverseEvent = inverseEvent;
    }

    public void activate() {
      limit = -1;
      activated = System.currentTimeMillis();
    }

    public void activateUntil(long until) {
      if (until < System.currentTimeMillis()) {
        return;
      }
      if (until == Long.MAX_VALUE) {
        activatePermanently();
        return;
      }
      limit = -1;
      activated = until - duration;
    }

    public void activateOnce() {
      if (permanent) {
        return;
      }
//      activated = System.currentTimeMillis();
      activated = System.currentTimeMillis() + 3_000 - duration;
      limit = 3;
      executed = 0;
    }

    public void activatePermanently() {
      permanent = true;
      activated = System.currentTimeMillis();
    }

    public boolean active() {
      if (permanent) {
        return true;
      }
      if (limit == -1) {
        return System.currentTimeMillis() - activated < duration;
      } else {
        return executed < limit;// && System.currentTimeMillis() - activated < 750;
      }
    }

    public AttackNerfStrategy strategy() {
      return type;
    }

    public long expiry() {
      return permanent ? Long.MAX_VALUE : activated + duration;
    }

    public long defaultDuration() {
      return duration;
    }

    public boolean inverseEvent() {
      return inverseEvent;
    }

    public Consumer<EntityDamageByEntityEvent> executor() {
      executed++;
      return executor;
    }

    public String name() {
      return type.typeName();
    }

    public boolean hidden() {
      return !show;
    }
  }
}
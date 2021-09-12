package de.jpx3.intave.user.meta;

import com.google.common.collect.Lists;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.player.dmc.DamageController;
import de.jpx3.intave.violation.mitigate.AttackNerfStrategy;
import de.jpx3.intave.violation.mitigate.HurtimeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE;
import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BLOCKING;

@Relocate
public final class PunishmentMetadata {
  public final static long DAMAGE_CANCEL_LIGHT_DURATION = 40_000;
  private final static long DAMAGE_CANCEL_MEDIUM_DURATION = 40_000;
  private final static long DAMAGE_CANCEL_HEAVY_DURATION = 5_000;
  private final static long BLOCKING_DAMAGE_CANCEL_DURATION = 10_000;

  private final static long DAMAGE_CANCEL_FIRST_HIT_DURATION = 60_000;
  private final static long ENTITY_HURT_TIME_CHANGE_DURATION = 5_000;

  private final static long GARBAGE_HITS_DURATION = 120_000;

  private final Map<AttackNerfStrategy, AttackNerfer> attackNerfersMap = new HashMap<>();
  private final List<AttackNerfer> attackNerfers;

  public int damageTicksBefore = -1;
  public int appliedDamageTicks = -1;
  public long timeLastBlockCancel;
  public long timeLastSneakToggleCancel;

  private Map<Integer, Long> lastTimeValidHurttimeAttack = new ConcurrentHashMap<>();
  private long delay = 600;

  public PunishmentMetadata(Player player) {
    this.attackNerfers = Lists.newArrayList(
      new AttackNerfer(
        AttackNerfStrategy.CANCEL, DAMAGE_CANCEL_HEAVY_DURATION,
        event -> event.setCancelled(true)
      ),
      new AttackNerfer(
        AttackNerfStrategy.CANCEL_FIRST_HIT, DAMAGE_CANCEL_FIRST_HIT_DURATION,
        event -> {}
      ),
      new AttackNerfer(
        AttackNerfStrategy.DMG_MEDIUM, DAMAGE_CANCEL_MEDIUM_DURATION,
        event -> DamageController.withNewDamageApplier(event, BASE, originalDamage -> -(originalDamage * 0.5))
      ),
      new AttackNerfer(
        AttackNerfStrategy.DMG_LIGHT, DAMAGE_CANCEL_LIGHT_DURATION,
        event -> DamageController.withNewDamageApplier(event, BASE, originalDamage -> -(originalDamage * 0.2))
      ),
      new AttackNerfer(AttackNerfStrategy.HT_MEDIUM, DAMAGE_CANCEL_MEDIUM_DURATION, event -> {
        // Perform hurt-time change
        int ticks = -ThreadLocalRandom.current().nextInt(4, 7);
        HurtimeModifier.applyHurtTimeChangeTo(player, (int) (DAMAGE_CANCEL_MEDIUM_DURATION / 50), ticks);
        // Perform hurt-time change on entity
        performEntityHurtTimeChange(event.getEntity());
      }),
      new AttackNerfer(AttackNerfStrategy.HT_LIGHT, DAMAGE_CANCEL_LIGHT_DURATION, event -> {
        // Perform hurt-time change
        int ticks = -ThreadLocalRandom.current().nextInt(3, 4);
        HurtimeModifier.applyHurtTimeChangeTo(player, (int) (DAMAGE_CANCEL_LIGHT_DURATION / 50), ticks);
      }),
      new AttackNerfer(AttackNerfStrategy.BLOCKING, BLOCKING_DAMAGE_CANCEL_DURATION, event -> {
        DamageController.withNewDamageApplier(event, BLOCKING, originalDamage -> 0d);
      }, true),
      new AttackNerfer(AttackNerfStrategy.GARBAGE_HITS, GARBAGE_HITS_DURATION, event -> {
        int entityId = event.getEntity().getEntityId();
        long lastValidAttack = System.currentTimeMillis() - lastTimeValidHurttimeAttack.computeIfAbsent(entityId, x -> 0L);
        if (lastValidAttack < delay) {
          event.setCancelled(true);
        } else {
          int random = ThreadLocalRandom.current().nextInt(-5, 10);
          if (random < 0) {
            delay = 550;
          } else if (random < 5) {
            delay = 600;
          } else if (random < 8) {
            delay = 650;
          } else {
            delay = 700;
          }
          lastTimeValidHurttimeAttack.put(entityId, System.currentTimeMillis());
        }
      })
    );
    for (AttackNerfer attackNerfer : attackNerfers) {
      this.attackNerfersMap.put(attackNerfer.type, attackNerfer);
    }
  }

  private void performEntityHurtTimeChange(Entity entity) {
    if (!(entity instanceof Player)) {
      return;
    }
    Player player = (Player) entity;
    int increase = 2;
    HurtimeModifier.applyHurtTimeChangeTo(player, (int) (ENTITY_HURT_TIME_CHANGE_DURATION / 50), increase);
  }

  public List<AttackNerfer> availableAttackNerfer() {
    return attackNerfers;
  }

  public AttackNerfer nerferOfType(AttackNerfStrategy type) {
    return attackNerfersMap.get(type);
  }

  public static final class AttackNerfer {
    private final AttackNerfStrategy type;
    private final Consumer<EntityDamageByEntityEvent> executor;
    private final long duration;
    private final boolean inverseEvent;
    private long activated;

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
      activated = System.currentTimeMillis();
    }

    public boolean active() {
      return System.currentTimeMillis() - activated < duration;
    }

    public boolean inverseEvent() {
      return inverseEvent;
    }

    public Consumer<EntityDamageByEntityEvent> executor() {
      return executor;
    }

    public String name() {
      return type.typeName();
    }
  }
}
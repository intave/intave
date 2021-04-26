package de.jpx3.intave.user;

import com.google.common.collect.Lists;
import de.jpx3.intave.event.punishment.AttackCancelType;
import de.jpx3.intave.event.punishment.EntityNoDamageTickChanger;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.annotate.Relocate;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Relocate
public final class UserMetaPunishmentData {
  public final static long DAMAGE_CANCEL_LIGHT_DURATION = 20_000;
  private final static long DAMAGE_CANCEL_MEDIUM_DURATION = 20_000;
  private final static long DAMAGE_CANCEL_HEAVY_DURATION = 5_000;
  private final static long BLOCKING_DAMAGE_CANCEL_DURATION = 5_000;

  private final static long ENTITY_HURT_TIME_CHANGE_DURATION = 5_000;

  private final List<DamageCancel> damageCancels;

  public int damageTicksBefore = -1;
  public int appliedDamageTicks = -1;
  public long timeLastBlockCancel;
  public long timeLastSneakToggleCancel;

  public UserMetaPunishmentData(Player player) {
    this.damageCancels = Lists.newArrayList(
      new DamageCancel(AttackCancelType.HEAVY, DAMAGE_CANCEL_HEAVY_DURATION, (event) -> event.setCancelled(true)),
      new DamageCancel(AttackCancelType.MEDIUM, DAMAGE_CANCEL_MEDIUM_DURATION, (event) -> {
        // Perform hurt-time change
        int ticks = -ThreadLocalRandom.current().nextInt(4, 7);
        EntityNoDamageTickChanger.applyHurtTimeChangeTo(player, (int) (DAMAGE_CANCEL_MEDIUM_DURATION / 50), ticks);
        // Perform hurt-time change on entity
        performEntityHurtTimeChange(event.getEntity());
      }),
      new DamageCancel(AttackCancelType.LIGHT, DAMAGE_CANCEL_LIGHT_DURATION, (event) -> {
        // Perform hurt-time change
        int ticks = -ThreadLocalRandom.current().nextInt(3, 4);
        EntityNoDamageTickChanger.applyHurtTimeChangeTo(player, (int) (DAMAGE_CANCEL_LIGHT_DURATION / 50), ticks);
      }),
      new DamageCancel(AttackCancelType.BLOCKING, BLOCKING_DAMAGE_CANCEL_DURATION, (event) -> {
        double blockingDamageAbsorption = event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING);
        if (blockingDamageAbsorption != 0) {
          event.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
        }
      })
    );
  }

  private void performEntityHurtTimeChange(Entity entity) {
    if (!(entity instanceof Player)) {
      return;
    }
    Player player = (Player) entity;
    int increase = 2;
    EntityNoDamageTickChanger.applyHurtTimeChangeTo(player, (int) (ENTITY_HURT_TIME_CHANGE_DURATION / 50), increase);
  }

  public List<DamageCancel> damageCancels() {
    return damageCancels;
  }

  public DamageCancel damageCancelOfType(AttackCancelType type) {
    for (DamageCancel damageCancel : damageCancels) {
      if (damageCancel.type == type) {
        return damageCancel;
      }
    }
    throw new IllegalStateException();
  }

  public static final class DamageCancel {
    private final AttackCancelType type;
    private final Consumer<EntityDamageByEntityEvent> executor;
    private final long duration;
    private long activated;

    public DamageCancel(
      AttackCancelType type,
      long duration,
      Consumer<EntityDamageByEntityEvent> executor
    ) {
      this.type = type;
      this.duration = duration;
      this.executor = executor;
    }

    public void activate() {
      activated = AccessHelper.now();
    }

    public boolean active() {
      return AccessHelper.now() - activated < duration;
    }

    public Consumer<EntityDamageByEntityEvent> executor() {
      return executor;
    }

    public String name() {
      return type.typeName();
    }
  }
}
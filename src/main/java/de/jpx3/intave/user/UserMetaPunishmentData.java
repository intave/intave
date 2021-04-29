package de.jpx3.intave.user;

import com.google.common.collect.Lists;
import de.jpx3.intave.event.punishment.AttackNerfStrategy;
import de.jpx3.intave.event.punishment.EntityNoDamageTickChanger;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.annotate.Relocate;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

@Relocate
public final class UserMetaPunishmentData {
  public final static long DAMAGE_CANCEL_LIGHT_DURATION = 20_000;
  private final static long DAMAGE_CANCEL_MEDIUM_DURATION = 20_000;
  private final static long DAMAGE_CANCEL_HEAVY_DURATION = 5_000;
  private final static long BLOCKING_DAMAGE_CANCEL_DURATION = 5_000;

  private final static long DAMAGE_CANCEL_FIRST_HIT_DURATION = 20_000;
  private final static long ENTITY_HURT_TIME_CHANGE_DURATION = 5_000;

  private final Map<AttackNerfStrategy, AttackNerfer> attackNerfersMap = new HashMap<>();
  private final List<AttackNerfer> attackNerfers;

  public int damageTicksBefore = -1;
  public int appliedDamageTicks = -1;
  public long timeLastBlockCancel;
  public long timeLastSneakToggleCancel;

  public UserMetaPunishmentData(Player player) {
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
        event -> event.setDamage(EntityDamageEvent.DamageModifier.BASE, event.getDamage(EntityDamageEvent.DamageModifier.BASE) * 0.7)
      ),
      new AttackNerfer(
        AttackNerfStrategy.DMG_LIGHT, DAMAGE_CANCEL_LIGHT_DURATION,
        event -> event.setDamage(EntityDamageEvent.DamageModifier.BASE, event.getDamage(EntityDamageEvent.DamageModifier.BASE) * 0.9)
      ),
      new AttackNerfer(AttackNerfStrategy.HT_MEDIUM, DAMAGE_CANCEL_MEDIUM_DURATION, event -> {
        // Perform hurt-time change
        int ticks = -ThreadLocalRandom.current().nextInt(4, 7);
        EntityNoDamageTickChanger.applyHurtTimeChangeTo(player, (int) (DAMAGE_CANCEL_MEDIUM_DURATION / 50), ticks);
        // Perform hurt-time change on entity
        performEntityHurtTimeChange(event.getEntity());
      }),
      new AttackNerfer(AttackNerfStrategy.HT_LIGHT, DAMAGE_CANCEL_LIGHT_DURATION, event -> {
        // Perform hurt-time change
        int ticks = -ThreadLocalRandom.current().nextInt(3, 4);
        EntityNoDamageTickChanger.applyHurtTimeChangeTo(player, (int) (DAMAGE_CANCEL_LIGHT_DURATION / 50), ticks);
      }),
      new AttackNerfer(AttackNerfStrategy.BLOCKING, BLOCKING_DAMAGE_CANCEL_DURATION, event -> {
        double blockingDamageAbsorption = event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING);
        if (blockingDamageAbsorption != 0) {
          event.setDamage(EntityDamageEvent.DamageModifier.BLOCKING, 0);
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
    EntityNoDamageTickChanger.applyHurtTimeChangeTo(player, (int) (ENTITY_HURT_TIME_CHANGE_DURATION / 50), increase);
  }

  public List<AttackNerfer> availableAttackNervers() {
    return attackNerfers;
  }

  public AttackNerfer nerferOfType(AttackNerfStrategy type) {
    return attackNerfersMap.get(type);
  }

  public static final class AttackNerfer {
    private final AttackNerfStrategy type;
    private final Consumer<EntityDamageByEntityEvent> executor;
    private final long duration;
    private long activated;

    public AttackNerfer(
      AttackNerfStrategy type,
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
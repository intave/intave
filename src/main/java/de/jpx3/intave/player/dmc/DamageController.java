package de.jpx3.intave.player.dmc;

import com.google.common.base.Function;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE;

public final class DamageController {
  public static void withNewDamageApplier(
    EntityDamageEvent damageEvent,
    DamageModifier modifier,
    UnaryOperator<Double> modifierFunction
  ) {
    Map<DamageModifier, Function<? super Double, Double>> damageModifierMap = modifierFunctionsOf(damageEvent);
    damageModifierMap.put(modifier, modifierFunction::apply);
    double baseDamage = damageEvent.getOriginalDamage(BASE);
    for (DamageModifier damageModifier : DamageModifier.values()) {
      if (!damageModifierMap.containsKey(damageModifier)) {
        continue;
      }
      Double apply = damageModifierMap.get(damageModifier).apply(baseDamage);
      damageEvent.setDamage(damageModifier, apply);
      baseDamage += apply;
    }
  }

  private final static Field DAMAGE_MODIFIER_FUNCTIONS_FIELD;

  static {
    try {
      DAMAGE_MODIFIER_FUNCTIONS_FIELD = EntityDamageEvent.class.getDeclaredField("modifierFunctions");
      DAMAGE_MODIFIER_FUNCTIONS_FIELD.setAccessible(true);
    } catch (NoSuchFieldException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static Map<DamageModifier, Function<? super Double, Double>> modifierFunctionsOf(EntityDamageEvent damageEvent) {
    try {
      //noinspection unchecked
      return (Map<DamageModifier, Function<? super Double, Double>>) DAMAGE_MODIFIER_FUNCTIONS_FIELD.get(damageEvent);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException(exception);
    }
  }
}

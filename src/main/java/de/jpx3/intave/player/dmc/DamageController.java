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
    double baseDamage = damageEvent.getDamage(BASE);
    for (DamageModifier damageModifier : DamageModifier.values()) {
      if (!damageModifierMap.containsKey(damageModifier)) {
        continue;
      }
      float apply = damageModifierMap.get(damageModifier).apply(baseDamage).floatValue();
      baseDamage += apply;
      if (!damageModifier.equals(BASE)) {
        damageEvent.setDamage(damageModifier, apply);
      }
    }
  }

  public static void refreshDamageChain(
    EntityDamageEvent damageEvent
  ) {
    Map<DamageModifier, Function<? super Double, Double>> damageModifierMap = modifierFunctionsOf(damageEvent);
    double baseDamage = damageEvent.getDamage(BASE);
    for (DamageModifier damageModifier : DamageModifier.values()) {
      if (!damageModifierMap.containsKey(damageModifier)) {
        continue;
      }
      float apply = damageModifierMap.get(damageModifier).apply(baseDamage).floatValue();
      baseDamage += apply;
      if (!damageModifier.equals(BASE)) {
        damageEvent.setDamage(damageModifier, apply);
      }
    }
  }

  private final static Field DAMAGE_MODIFIER_FUNCTION_FIELD;

  static {
    try {
      DAMAGE_MODIFIER_FUNCTION_FIELD = EntityDamageEvent.class.getDeclaredField("modifierFunctions");
      DAMAGE_MODIFIER_FUNCTION_FIELD.setAccessible(true);
    } catch (NoSuchFieldException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static Map<DamageModifier, Function<? super Double, Double>> modifierFunctionsOf(EntityDamageEvent damageEvent) {
    try {
      //noinspection unchecked
      return (Map<DamageModifier, Function<? super Double, Double>>) DAMAGE_MODIFIER_FUNCTION_FIELD.get(damageEvent);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException(exception);
    }
  }
}

package de.jpx3.intave.block.physics;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.bukkit.Material.*;

public final class BlockProperties {
  private static final Property DEFAULT_PROPERTY = Property.builderFor(AIR).build();
  private static final Map<Material, Property> registry = new HashMap<>();

  public static void setup() {
    Property.builderFor(ICE, PACKED_ICE, "FROSTED_ICE").slipperiness(0.98f).buildAndSave();
    Property.builderFor("BLUE_ICE").slipperiness(0.989f).buildAndSave();
    Property.builderFor(SLIME_BLOCK).slipperiness(0.8f).buildAndSave();
    Property.builderFor(LADDER, VINE).climbable().buildAndSave();
    Property.builderFor("SCAFFOLDING").climbable().withoutClimbableSneakLimit().buildAndSave();
    Property.builderFor("WEEPING_VINES", "WEEPING_VINES_PLANT").climbable().buildAndSave();
    Property.builderFor("TWISTING_VINES", "TWISTING_VINES_PLANT").climbable().buildAndSave();
    Property.builderFor("CAVE_VINES_PLANT").climbable().buildAndSave();
    Property.builderFor(SOUL_SAND).speedFactor(0.4f).soulSpeedAffected().buildAndSave();
    Property.builderFor("SOUL_SOIL").soulSpeedAffected().buildAndSave();
    Property.builderFor("HONEY_BLOCK").jumpFactor(0.5f).speedFactor(0.4f).buildAndSave();
  }

  private static void append(Material material, Property property) {
    registry.put(material, property);
  }

  public static Property of(Material material) {
    return registry.getOrDefault(material, DEFAULT_PROPERTY);
  }

  public static final class Property {
    private final Material[] materials;
    private final float slipperiness;
    private final float jumpFactor;
    private final float speedFactor;
    private final boolean climbable;
    private final boolean climbableSneakLimit;
    private final boolean soulSpeedAffected;

    public Property(
      Material[] materials,
      float slipperiness,
      float jumpFactor,
      float speedFactor,
      boolean climbable,
      boolean climbableSneakLimit, boolean soulSpeedAffected
    ) {
      this.materials = materials;
      this.slipperiness = slipperiness;
      this.jumpFactor = jumpFactor;
      this.speedFactor = speedFactor;
      this.climbable = climbable;
      this.climbableSneakLimit = climbableSneakLimit;
      this.soulSpeedAffected = soulSpeedAffected;
    }

    public void trySave() {
      ifPresent(BlockProperties::append);
    }

    public void ifPresent(BiConsumer<? super Material, ? super Property> consumer) {
      for (Material material : materials) {
        if (material != null) {
          consumer.accept(material, this);
        }
      }
    }

    public float slipperiness() {
      return slipperiness;
    }

    public float jumpFactor() {
      return jumpFactor;
    }

    public float speedFactor() {
      return speedFactor;
    }

    public boolean climbable() {
      return climbable;
    }

    public boolean climbableSneakLimit() {
      return climbableSneakLimit;
    }

    public boolean soulSpeedAffected() {
      return soulSpeedAffected;
    }

    public static Builder builderFor(Object... materials) {
      Material[] materialArray = new Material[materials.length];
      for (int i = 0; i < materials.length; i++) {
        Object material = materials[i];
        if (material instanceof Material) {
          materialArray[i] = (Material) material;
        } else if (material instanceof String) {
          materialArray[i] = Material.getMaterial((String) material);
        } else {
          materialArray[i] = null;
        }
      }
      return new Builder(materialArray);
    }

    public static final class Builder {
      private final Material[] materials;
      private float slipperiness = 0.6f;
      private float jumpFactor = 1.0f;
      private float speedFactor = 1.0f;
      private boolean climbable = false;
      private boolean climbableSneakLimit = true;
      private boolean soulSpeedAffected = false;

      public Builder(Material... materials) {
        this.materials = materials;
      }

      public Builder slipperiness(float slipperiness) {
        this.slipperiness = slipperiness;
        return this;
      }

      public Builder jumpFactor(float jumpFactor) {
        this.jumpFactor = jumpFactor;
        return this;
      }

      public Builder speedFactor(float speedFactor) {
        this.speedFactor = speedFactor;
        return this;
      }

      public Builder climbable() {
        this.climbable = true;
        return this;
      }

      public Builder soulSpeedAffected() {
        this.soulSpeedAffected = true;
        return this;
      }

      public Builder withoutClimbableSneakLimit() {
        this.climbableSneakLimit = false;
        return this;
      }

      public Property build() {
        return new Property(materials, slipperiness, jumpFactor, speedFactor, climbable, climbableSneakLimit, soulSpeedAffected);
      }

      public void buildAndSave() {
        build().trySave();
      }
    }
  }
}
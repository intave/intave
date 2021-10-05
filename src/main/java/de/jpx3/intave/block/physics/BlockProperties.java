package de.jpx3.intave.block.physics;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class BlockProperties {
  private final static Property DEFAULT_PROPERTY = Property.of(Material.AIR).build();
  private final static Map<Material, Property> registry = new HashMap<>();

  public static void setup() {
    Property.of(Material.ICE).slipperiness(0.98f).build().saveIfPresent();
    Property.of(Material.SLIME_BLOCK).slipperiness(0.8f).build().saveIfPresent();
    Property.of(Material.PACKED_ICE).slipperiness(0.98f).build().saveIfPresent();
    Property.of("FROSTED_ICE").slipperiness(0.98F).build().saveIfPresent();
    Property.of("BLUE_ICE").slipperiness(0.989F).build().saveIfPresent();
    Property.of(Material.LADDER).climbable().build().saveIfPresent();
    Property.of(Material.VINE).climbable().build().saveIfPresent();
    Property.of("SCAFFOLDING").climbable().build().saveIfPresent();
    Property.of("WEEPING_VINES").climbable().build().saveIfPresent();
    Property.of("WEEPING_VINES_PLANT").climbable().build().saveIfPresent();
    Property.of("TWISTING_VINES").climbable().build().saveIfPresent();
    Property.of("TWISTING_VINES_PLANT").climbable().build().saveIfPresent();
    Property.of("CAVE_VINES_PLANT").climbable().build().saveIfPresent();
    Property.of(Material.SOUL_SAND).speedFactor(0.4f).soulSpeedAffected().build().saveIfPresent();
    Property.of("SOUL_SOIL").soulSpeedAffected().build().saveIfPresent();
    Property.of("HONEY_BLOCK").jumpFactor(0.5f).speedFactor(0.4f).build().saveIfPresent();
  }

  private static void append(Property property, Material material) {
    registry.put(material, property);
  }

  public static Property ofType(Material material) {
    return registry.getOrDefault(material, DEFAULT_PROPERTY);
  }

  public static final class Property {
    private final Material material;
    private final float slipperiness;
    private final float jumpFactor;
    private final float speedFactor;
    private final boolean climbable;
    private final boolean soulSpeedAffected;

    public Property(
      Material material,
      float slipperiness,
      float jumpFactor,
      float speedFactor,
      boolean climbable,
      boolean soulSpeedAffected
    ) {
      this.material = material;
      this.slipperiness = slipperiness;
      this.jumpFactor = jumpFactor;
      this.speedFactor = speedFactor;
      this.climbable = climbable;
      this.soulSpeedAffected = soulSpeedAffected;
    }

    public void saveIfPresent() {
      ifPresent(BlockProperties::append);
    }

    public void ifPresent(BiConsumer<Property, Material> consumer) {
      if (this.material != null) {
        consumer.accept(this, this.material);
      }
    }

    public static Builder of(Material material) {
      return new Builder(material);
    }

    public static Builder of(String material) {
      return new Builder(Material.getMaterial(material));
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

    public boolean soulSpeedAffected() {
      return soulSpeedAffected;
    }

    public static final class Builder {
      private final Material material;
      private float slipperiness = 0.6f;
      private float jumpFactor = 1.0f;
      private float speedFactor = 1.0f;
      private boolean climbable = false;
      private boolean soulSpeedAffected = false;

      public Builder(Material material) {
        this.material = material;
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

      public Property build() {
        return new Property(material, slipperiness, jumpFactor, speedFactor, climbable, soulSpeedAffected);
      }
    }
  }
}
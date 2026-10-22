/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.user.meta;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

public final class MetadataBundle {
  private final AbilityMetadata ability;
  private final AttackMetadata attack;
  private final CombatMetadata combat;
  private final ConnectionMetadata connection;
  private final EffectMetadata effect;
  private final InventoryMetadata inventory;
  private final MovementMetadata movement;
  private final ProtocolMetadata protocol;
  private final PunishmentMetadata punishment;
  private final ViolationMetadata violation;

  public MetadataBundle(Player player, User user) {
    this.ability = new AbilityMetadata(player);
    this.attack = new AttackMetadata(player);
    this.combat = new CombatMetadata(MinecraftVersions.VER1_9_0.atOrAbove());
    this.connection = new ConnectionMetadata(player);
    this.effect = new EffectMetadata(player);
    this.inventory = new InventoryMetadata(player, user);
    this.movement = new MovementMetadata(player, user);
    this.protocol = new ProtocolMetadata(player, user);
    this.punishment = new PunishmentMetadata(player);
    this.violation = new ViolationMetadata();
  }

  public AbilityMetadata abilities() {
    return ability;
  }

  public AttackMetadata attack() {
    return attack;
  }

  public CombatMetadata combat() {
    return combat;
  }

  public ConnectionMetadata connection() {
    return connection;
  }

  public EffectMetadata potions() {
    return effect;
  }

  public InventoryMetadata inventory() {
    return inventory;
  }

  public MovementMetadata movement() {
    return movement;
  }

  public ProtocolMetadata protocol() {
    return protocol;
  }

  public PunishmentMetadata punishment() {
    return punishment;
  }

  public ViolationMetadata violationLevel() {
    return violation;
  }

  public void setup() {
    movement.setup();
  }
}

package de.jpx3.intave.user.meta;

import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

@Relocate
public final class MetadataBundle {
  private final AbilityMetadata ability;
  private final AttackMetadata attack;
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
    this.connection = new ConnectionMetadata(player);
    this.effect = new EffectMetadata(player);
    this.inventory = new InventoryMetadata(player);
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

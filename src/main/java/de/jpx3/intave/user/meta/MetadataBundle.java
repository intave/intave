package de.jpx3.intave.user.meta;

import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

@Relocate
public final class MetadataBundle {
  private final ViolationMetadata violationLevelData;
  private final MovementMetadata movementData;
  private final AbilityMetadata abilityData;
  private final EffectMetadata potionData;
  private final ProtocolMetadata clientData;
  private final ConnectionMetadata connectionData;
  private final InventoryMetadata inventoryData;
  private final AttackMetadata attackData;
  private final PunishmentMetadata punishmentData;

  public MetadataBundle(Player player, User user) {
    this.violationLevelData = new ViolationMetadata();
    this.clientData = new ProtocolMetadata(player, user);
    this.abilityData = new AbilityMetadata(player);
    this.potionData = new EffectMetadata(player);
    this.inventoryData = new InventoryMetadata(player);
    this.connectionData = new ConnectionMetadata(player);
    this.movementData = new MovementMetadata(player, user);
    this.attackData = new AttackMetadata(player);
    this.punishmentData = new PunishmentMetadata(player);
  }

  public ViolationMetadata violationLevel() {
    return violationLevelData;
  }

  public MovementMetadata movement() {
    return movementData;
  }

  public InventoryMetadata inventory() {
    return inventoryData;
  }

  public AbilityMetadata abilities() {
    return abilityData;
  }

  public EffectMetadata potions() {
    return potionData;
  }

  public ConnectionMetadata connection() {
    return connectionData;
  }

  public ProtocolMetadata protocol() {
    return clientData;
  }

  public AttackMetadata attack() {
    return attackData;
  }

  public PunishmentMetadata punishment() {
    return punishmentData;
  }

  public void setup() {
    movementData.setup();
  }
}

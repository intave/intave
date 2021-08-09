package de.jpx3.intave.user;

import org.bukkit.entity.Player;

public final class UserMeta {
  private final UserMetaViolationLevelData violationLevelData;
  private final UserMetaMovementData movementData;
  private final UserMetaAbilityData abilityData;
  private final UserMetaPotionData potionData;
  private final UserMetaClientData clientData;
  private final UserMetaConnectionData connectionData;
  private final UserMetaInventoryData inventoryData;
  private final UserMetaAttackData attackData;
  private final UserMetaPunishmentData punishmentData;

  public UserMeta(Player player, User user) {
    this.violationLevelData = new UserMetaViolationLevelData();
    this.clientData = new UserMetaClientData(player, user);
    this.abilityData = new UserMetaAbilityData(player);
    this.potionData = new UserMetaPotionData(player);
    this.inventoryData = new UserMetaInventoryData(player);
    this.connectionData = new UserMetaConnectionData(player);
    this.movementData = new UserMetaMovementData(player, user);
    this.attackData = new UserMetaAttackData(player);
    this.punishmentData = new UserMetaPunishmentData(player);
  }

  public UserMetaViolationLevelData violationLevelData() {
    return violationLevelData;
  }

  public UserMetaMovementData movementData() {
    return movementData;
  }

  public UserMetaInventoryData inventoryData() {
    return inventoryData;
  }

  public UserMetaAbilityData abilityData() {
    return abilityData;
  }

  public UserMetaPotionData potionData() {
    return potionData;
  }

  public UserMetaConnectionData connectionData() {
    return connectionData;
  }

  public UserMetaClientData clientData() {
    return clientData;
  }

  public UserMetaAttackData attackData() {
    return attackData;
  }

  public UserMetaPunishmentData punishmentData() {
    return punishmentData;
  }

  public void setup() {
    movementData.setup();
  }
}

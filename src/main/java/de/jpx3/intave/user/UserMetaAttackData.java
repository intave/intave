package de.jpx3.intave.user;

import de.jpx3.intave.event.service.entity.ClientSideEntityService;
import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.tools.annotate.Nullable;
import de.jpx3.intave.tools.client.PlayerRotationHelper;
import org.bukkit.entity.Player;

public final class UserMetaAttackData {
  private final Player player;
  private double lastReach;
  private int lastAttackedEntityID = -1;

  private long lastAttack = 0;
  private long lastEntitySwitch = 0;

  private WrappedEntity lastAttackedEntity;
  private float perfectYaw, perfectPitch;

  public UserMetaAttackData(Player player) {
    this.player = player;
  }

  public void updatePerfectRotation() {
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    double positionX = movementData.positionX;
    double positionY = movementData.positionY;
    double positionZ = movementData.positionZ;

    // Set prefect yaw & pitch
    if (lastAttackedEntity != null) {
      WrappedEntity.EntityPositionContext positions = lastAttackedEntity.position;
      perfectYaw = PlayerRotationHelper.resolveYawRotation(positions, positionX, positionZ);
      perfectPitch = PlayerRotationHelper.resolvePitchRotation(positions, positionX, positionY, positionZ);
    }
  }

  public boolean recentlyAttacked(long time) {
    return AccessHelper.now() - lastAttack <= time;
  }

  public boolean recentlySwitchedEntity(long time) {
    return AccessHelper.now() - lastEntitySwitch <= time;
  }

  public double lastReach() {
    return lastReach;
  }

  public int lastAttackedEntityID() {
    return lastAttackedEntityID;
  }

  public float perfectYaw() {
    return perfectYaw;
  }

  public float perfectPitch() {
    return perfectPitch;
  }

  @Nullable
  public WrappedEntity lastAttackedEntity() {
    return lastAttackedEntity;
  }

  public void setLastReach(double lastReach) {
    this.lastReach = lastReach;
  }

  public void setLastAttackedEntityID(int lastAttackedEntityID) {
    this.lastAttackedEntityID = lastAttackedEntityID;

    WrappedEntity lastAttackedEntity = this.lastAttackedEntity;
    WrappedEntity attackedEntity = ClientSideEntityService.entityByIdentifier(UserRepository.userOf(player), lastAttackedEntityID);
    if (attackedEntity != null && attackedEntity != lastAttackedEntity) {
      this.lastEntitySwitch = AccessHelper.now();
    }

    this.lastAttackedEntity = attackedEntity;
    this.lastAttack = AccessHelper.now();
  }
}
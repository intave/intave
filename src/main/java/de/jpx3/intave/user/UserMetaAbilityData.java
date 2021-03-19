package de.jpx3.intave.user;

import de.jpx3.intave.event.dispatch.PlayerAbilityEvaluator;
import org.bukkit.entity.Player;

public final class UserMetaAbilityData {
  private boolean flying;
  private boolean allowFlying;

  private PlayerAbilityEvaluator.GameMode gameMode;
  private PlayerAbilityEvaluator.GameMode pendingGameMode;

  private float flySpeed = 0.05f;
  private float walkSpeed = 0.1f;

  public UserMetaAbilityData(Player player) {
    boolean hasPlayer = (player != null);
    if(hasPlayer) {
      this.allowFlying = player.getAllowFlight();
      this.flying = player.isFlying();
    } else {
      this.allowFlying = this.flying = false;
    }
  }

  public boolean inGameModeIncludePending(PlayerAbilityEvaluator.GameMode gameMode) {
    return this.gameMode == gameMode || this.pendingGameMode == gameMode;
  }

  public boolean flying() {
    return flying;
  }

  public boolean allowFlying() {
    return allowFlying;
  }

  public float walkSpeed() {
    return walkSpeed;
  }

  public float flySpeed() {
    return flySpeed;
  }

  public void flying(boolean flying) {
    this.flying = flying;
  }

  public void setAllowFlying(boolean allowFlying) {
    this.allowFlying = allowFlying;
  }

  public void setWalkSpeed(float walkSpeed) {
    this.walkSpeed = walkSpeed;
  }

  public void setFlySpeed(float flySpeed) {
    this.flySpeed = flySpeed;
  }

  public void setGameMode(PlayerAbilityEvaluator.GameMode gameMode) {
    this.gameMode = gameMode;
  }

  public void setPendingGameMode(PlayerAbilityEvaluator.GameMode pendingGameMode) {
    this.pendingGameMode = pendingGameMode;
  }
}
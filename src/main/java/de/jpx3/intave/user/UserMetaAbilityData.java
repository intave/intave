package de.jpx3.intave.user;

import de.jpx3.intave.event.dispatch.PlayerAbilityEvaluator;
import de.jpx3.intave.tools.annotate.Relocate;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Arrays;

@Relocate
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
      setupDefaultGameMode(player.getGameMode());
    } else {
      this.allowFlying = this.flying = false;
    }
  }

  private void setupDefaultGameMode(GameMode gameMode) {
    int gameModeValue = gameMode.getValue();
    this.gameMode = Arrays.stream(PlayerAbilityEvaluator.GameMode.values())
      .filter(mode -> mode.id() == gameModeValue)
      .findFirst()
      .orElse(PlayerAbilityEvaluator.GameMode.NOT_SET);
    this.pendingGameMode = this.gameMode;
  }

  public boolean inGameModeIncludePending(PlayerAbilityEvaluator.GameMode gameMode) {
    return this.gameMode == gameMode || this.pendingGameMode == gameMode;
  }

  public boolean inGameMode(GameMode gameMode) {
    return this.gameMode.id() == gameMode.getValue();
  }

  public boolean inGameMode(PlayerAbilityEvaluator.GameMode gameMode) {
    return this.gameMode == gameMode;
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

  public void setFlying(boolean flying) {
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
    if (this.gameMode == PlayerAbilityEvaluator.GameMode.SPECTATOR && gameMode == PlayerAbilityEvaluator.GameMode.CREATIVE) {
      setAllowFlying(true);
      setFlying(true);
    }
    this.gameMode = gameMode;
  }

  public void setPendingGameMode(PlayerAbilityEvaluator.GameMode pendingGameMode) {
    this.pendingGameMode = pendingGameMode;
  }
}
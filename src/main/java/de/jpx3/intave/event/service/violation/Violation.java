package de.jpx3.intave.event.service.violation;

import com.google.common.base.Preconditions;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.tools.AccessHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class Violation {
  private final Class<? extends IntaveCheck> checkClass;
  private final UUID playerid;
  private final String baseMessage;
  private final String details;
  private final String threshold;
  private final double addedViolationPoints;
  private final int optionFlags;

  private Violation(
    Class<? extends IntaveCheck> checkClass,
    UUID playerid,
    String baseMessage,
    String details,
    String threshold,
    double addedViolationPoints,
    int optionFlags
  ) {
    this.checkClass = checkClass;
    this.playerid = playerid;
    this.baseMessage = baseMessage;
    this.details = details;
    this.threshold = threshold;
    this.addedViolationPoints = addedViolationPoints;
    this.optionFlags = optionFlags;
  }

  public IntaveCheck check() {
    return IntavePlugin.singletonInstance().checkService().searchCheck(checkClass);
  }

  public Class<? extends IntaveCheck> checkClass() {
    return checkClass;
  }

  public Optional<Player> player() {
    Player player = Bukkit.getPlayer(playerid);
    return AccessHelper.isOnline(player) ? Optional.of(player) : Optional.empty();
  }

  public UUID playerId() {
    return playerid;
  }

  public String message() {
    return baseMessage;
  }

  public String details() {
    return details;
  }

  public String threshold() {
    return threshold;
  }

  public double addedViolationPoints() {
    return addedViolationPoints;
  }

  public boolean flagSet(int flag) {
    return ViolationFlags.matches(optionFlags, flag);
  }

  public static Builder fromType(Class<? extends IntaveCheck> checkClass) {
    return new Builder(checkClass);
  }

  public static class Builder {
    private final Class<? extends IntaveCheck> checkClass;
    private UUID playerid;
    private String baseMessage;
    private String details;
    private String threshold;
    private double addedViolationPoints;
    private int optionFlags = 0;

    private boolean constructed;

    public Builder(Class<? extends IntaveCheck> checkClass) {
      this.checkClass = checkClass;
    }

    public Builder withPlayer(Player player) {
      this.playerid = player.getUniqueId();
      return this;
    }

    public Builder withMessage(String baseMessage) {
      this.baseMessage = baseMessage;
      return this;
    }

    public Builder withDetails(String details) {
      this.details = details;
      return this;
    }

    public Builder setFlags(int flags) {
      this.optionFlags = flags;
      return this;
    }

    public Builder appendFlags(int flags) {
      this.optionFlags |= flags;
      return this;
    }

    public Builder clearFlags() {
      this.optionFlags = 0;
      return this;
    }

    public Builder withDefaultThreshold() {
      this.threshold = "thresholds";
      return this;
    }

    public Builder withCustomThreshold(String threshold) {
      this.threshold = threshold;
      return this;
    }

    public Builder withVL(double addedViolationPoints) {
      this.addedViolationPoints = addedViolationPoints;
      return this;
    }

    public Violation build() {
      if(constructed) {
        throw new IllegalStateException();
      }
      constructed = true;
      Preconditions.checkNotNull(checkClass);
      Preconditions.checkNotNull(playerid);
      Preconditions.checkNotNull(baseMessage);
      if(details == null || details.isEmpty()) {
        details = "";
      }
      if(addedViolationPoints < 0) {
        throw new IllegalStateException("Can not have negative VL");
      }
      if(threshold == null) {
        withDefaultThreshold();
      }
      return new Violation(checkClass, playerid, baseMessage, details, threshold, addedViolationPoints, optionFlags);
    }
  }

  public static class ViolationFlags {
    public static int DONT_PROCESS_VIOSTAT = 1;
    public static int OPTION_TWO = 1 << 1;

    public static boolean matches(int optionFlags, int optionFlag) {
      return (optionFlags & optionFlag) > 0;
    }
  }
}

package de.jpx3.intave.module.violation;

import com.google.common.base.Preconditions;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * A {@link Violation} serves as threat-assessment submitted by a {@link Check}.<br>
 * It contains a base message, details, the threshold-channel,
 * the amount of vl points to add and option flags.
 * It is submitted to a {@link ViolationProcessor}.
 *
 * @see Check
 * @see ViolationContext
 * @see ViolationProcessor
 */
public final class Violation {
  private final Class<? extends Check> checkClass;
  private final UUID id;
  private final String baseMessage;
  private final String details;
  private final String threshold;
  private final double addedViolationPoints;
  private final int optionFlags;

  private Violation(
    Class<? extends Check> checkClass,
    UUID id,
    String baseMessage,
    String details,
    String threshold,
    double addedViolationPoints,
    int optionFlags
  ) {
    this.checkClass = checkClass;
    this.id = id;
    this.baseMessage = baseMessage;
    this.details = details;
    this.threshold = threshold;
    this.addedViolationPoints = addedViolationPoints;
    this.optionFlags = optionFlags;
  }

  public Check check() {
    return IntavePlugin.singletonInstance().checks().searchCheck(checkClass);
  }

  public Class<? extends Check> checkClass() {
    return checkClass;
  }

  public Optional<Player> findPlayer() {
    Player player = Bukkit.getPlayer(id);
    return isOnline(player) ? Optional.of(player) : Optional.empty();
  }

  private boolean isOnline(OfflinePlayer player) {
    return player != null && (player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != null);
  }

  public UUID playerId() {
    return id;
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

  public static Builder builderFor(Class<? extends Check> checkClass) {
    return new Builder(checkClass);
  }

  public static class Builder {
    private final Class<? extends Check> checkClass;
    private UUID playerid;
    private String baseMessage;
    private String details;
    private String threshold;
    private double addedViolationPoints;
    private int optionFlags = 0;

    private boolean constructed;

    public Builder(Class<? extends Check> checkClass) {
      this.checkClass = checkClass;
    }

    public Builder forPlayer(Player player) {
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

    public synchronized Violation build() {
      if (constructed) {
        throw new IllegalStateException();
      }
      constructed = true;
      Preconditions.checkNotNull(checkClass);
      Preconditions.checkNotNull(playerid);
      Preconditions.checkNotNull(baseMessage);
      if (details == null) {
        details = "";
      }
      baseMessage = baseMessage.trim();
      details = details.trim();
      if (addedViolationPoints < 0) {
        throw new IllegalStateException("Can not have negative VL");
      }
      if (threshold == null) {
        withDefaultThreshold();
      }
      return new Violation(checkClass, playerid, baseMessage, details, threshold, addedViolationPoints, optionFlags);
    }
  }

  public static class ViolationFlags {
    public static int DONT_PROCESS_VIOSTAT = 1;
    public static int OPTION_TWO = 1 << 1;

    public static boolean matches(int optionFlags, int optionFlag) {
      return (optionFlags & optionFlag) != 0;
    }
  }
}

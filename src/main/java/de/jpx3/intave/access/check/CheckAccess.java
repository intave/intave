package de.jpx3.intave.access.check;

import de.jpx3.intave.access.IntaveAccess;
import de.jpx3.intave.access.player.UnknownPlayerException;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * This class provides a access environment for a specified check in Intave.
 * All checks have their own and unique {@link CheckAccess} at their disposal.
 * Access to their implementations is granted via {@link IntaveAccess#check(Check)} and {@link IntaveAccess#check(String)}
 */
public interface CheckAccess {
  /**
   * Returns the name of the underlying check
   * @return the name of the underlying check
   */
  String name();

  /**
   * Returns the corresponding {@link Check} enum setting for this check
   * @return the corresponding {@link Check} enum setting for this check
   */
  Check enumCheck();

  /**
   * Return wheter this check is enabled
   * @return true when this check is enabled, false if not
   */
  boolean enabled();

  /**
   * Retrieves a players violation level for this check for the "thresholds" threshold
   * @param player the player
   * @return the players violation level
   * @throws UnknownPlayerException when the player couldn't be found
   */
  default double violationLevelOf(Player player) throws UnknownPlayerException {
    return violationLevelOf(player, "thresholds");
  }

  /**
   * Retrieves a players violation level for this check for a custom threshold
   * @param player the player
   * @param threshold the requested threshold slot
   * @return the players violation level
   * @throws UnknownPlayerException when the player couldn't be found
   */
  double violationLevelOf(Player player, String threshold) throws UnknownPlayerException;

  /**
   * Increments a players violation level for this check for the "thresholds" threshold
   * @param player the player
   * @param amount the amount to increment the violation level by
   * @throws UnknownPlayerException when the player couldn't be found
   */
  default void addViolationPoints(Player player, double amount) throws UnknownPlayerException {
    addViolationPoints(player, "thresholds", amount);
  }

  /**
   * Increments a players violation level for this check for a custom threshold
   * @param player the player
   * @param threshold the requested threshold slot
   * @param amount the amount to increment the violation level by
   * @throws UnknownPlayerException when the player couldn't be found
   */
  void addViolationPoints(Player player, String threshold, double amount) throws UnknownPlayerException;

  /**
   * Resets a players violation level for this check for the "thresholds" threshold
   * @param player the player
   * @throws UnknownPlayerException when the player couldn't be found
   */
  default void resetViolationLevel(Player player) throws UnknownPlayerException {
    resetViolationLevel(player, "thresholds");
  }

  /**
   * Resets a players violation level for this check for the "thresholds" threshold
   * @param player the player
   * @param threshold the requested threshold slot
   * @throws UnknownPlayerException when the player couldn't be found
   */
  void resetViolationLevel(Player player, String threshold) throws UnknownPlayerException;

  /**
   * Retrieves the set mitigation strategy for this check.
   * If a check does not support mitigation strategies, this method will return {@link MitigationStrategy#NOT_SUPPORTED}
   * @return the set mitigation strategy for this check, otherwise {@link MitigationStrategy#NOT_SUPPORTED}
   */
  default MitigationStrategy mitigationStrategy() {
    return MitigationStrategy.NOT_SUPPORTED;
  }

  /**
   * Overrides set mitigation strategy for this check.
   * @throws UnsupportedOperationException when the check does not support mitigation strategies
   */
  default void setMitigationStrategy(MitigationStrategy mitigationStrategy) {
    throw new UnsupportedOperationException("Check " + name() + " does not support a mitigation strategy");
  }

  /**
   * Retrieves this checks threshold-commands by threshold
   * @param threshold the selected theshold slot
   * @return a map of thresholds to commands
   */
  Map<Integer, List<String>> commandsOf(String threshold);

  /**
   * Retrieves this checks statistics
   * @return this checks statistics
   */
  CheckStatisticsAccess statistics();
}
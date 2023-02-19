package de.jpx3.intave.player;

import de.jpx3.intave.IntaveLogger;
import org.bukkit.configuration.ConfigurationSection;

public final class FaultKicks {
  public static boolean POSITION_FAULTS = true;
  public static boolean MISSING_POSITION_UPDATE = true;
  public static boolean INVALID_PLAYER_ACTION = true;
  public static boolean FEEDBACK_FAULTS = true;
  public static boolean IGNORING_FEEDBACK = true;
  public static boolean IGNORING_KEEP_ALIVE = true;
  public static boolean INVALID_KEY_INPUT = true;

  public static void applyFrom(ConfigurationSection section) {
    POSITION_FAULTS = loadFrom(section, "position-faults", "position faults");
    MISSING_POSITION_UPDATE = loadFrom(section, "missing-position-update", "missing position updates");
    FEEDBACK_FAULTS = loadFrom(section, "feedback-faults", "feedback faults");
    IGNORING_FEEDBACK = loadFrom(section, "ignoring-feedback", "ignoring feedback packets");
    IGNORING_KEEP_ALIVE = loadFrom(section, "ignoring-keep-alive", "ignoring keep alive packets");
    INVALID_KEY_INPUT = loadFrom(section, "invalid-key-input", "invalid key inputs");
    INVALID_PLAYER_ACTION = loadFrom(section, "invalid-player-action", "invalid player actions");
  }

  private static boolean loadFrom(ConfigurationSection section, String key, String warnMessage) {
    boolean value = section == null || section.getBoolean(key, true);
    if (!value) {
      IntaveLogger.logger().warn("Disabled fault kicks for " + warnMessage + " (not recommended)");
    }
    return value;
  }
}

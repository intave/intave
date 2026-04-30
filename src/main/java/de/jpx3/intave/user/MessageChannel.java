package de.jpx3.intave.user;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public enum MessageChannel {
  VIOLATION_SIMPLE("intave.command.verbose", false),
  VIOLATION_FINE("intave.command.verbose", false),
  NOTIFY("intave.command.notify", true),
  COMBAT_MODIFIERS("intave.command.combatmodifiers", false),
  DEBUG_TELEPORT("intave.command.verbose", false),
  DEBUG_MOUNTS("intave.command.verbose", false),
  DEBUG_ITEM_RESETS("intave.command.verbose", false),
  DEBUG_BLOCK_CACHE("intave.command.verbose", false),
  DEBUG_POSITION("intave.command.verbose", false),
  DEBUG_PACKET_HOLD("intave.command.verbose", false),
  DEBUG_AUTOMATION("intave.command.verbose", false),

  ;

  final String permission;
  final boolean enabledByDefault;

  MessageChannel(String permission, boolean enabledByDefault) {
    this.permission = permission;
    this.enabledByDefault = enabledByDefault;
  }

  public String permission() {
    return permission;
  }

  public boolean isEnabledByDefault() {
    return enabledByDefault;
  }
}

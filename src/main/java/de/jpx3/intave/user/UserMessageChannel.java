package de.jpx3.intave.user;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public enum UserMessageChannel {
  VERBOSE("intave.command.verbose", false),
  NOTIFY("intave.command.notify", true);

  final String permission;
  final boolean enabledByDefault;

  UserMessageChannel(String permission, boolean enabledByDefault) {
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

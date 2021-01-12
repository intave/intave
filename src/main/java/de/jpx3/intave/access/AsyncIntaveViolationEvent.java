package de.jpx3.intave.access;

import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.IntavePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * Created by Jpx3 on 10.11.2017.
 */

public final class AsyncIntaveViolationEvent extends AbstractIntaveExternalEvent implements Cancellable {
  private Player punished;
  private int legacyProtocolVersion;
  private String checkName;
  private String message;
  private String details;
  private double vlBefore;
  private double vlAfter;
  private boolean cancelled;

  private AsyncIntaveViolationEvent(
    Player punished,
    int legacyProtocolVersion,
    String checkName,
    String message,
    String details,
    double vlBefore,
    double vlAfter
  ) {
    this.punished = punished;
    this.legacyProtocolVersion = legacyProtocolVersion;
    this.checkName = checkName;
    this.message = message;
    this.details = details;
    this.vlBefore = vlBefore;
    this.vlAfter = vlAfter;
  }

  public Player detectedPlayer() {
    return punished;
  }

  public PlayerAccess detectedPlayerAccess() {
    return IntaveAccessor.unsafeAccess().player(detectedPlayer());
  }

  public int legacyProtocolVersion() {
    return legacyProtocolVersion;
  }

  public String checkName() {
    return checkName;
  }

  public String message(MessageSpecifier messageSpecifier) {
    switch (messageSpecifier) {
      case FULL:
        return message + " " + details.trim();
      case COMPACT:
        return message;
    }
    return "invalid";
  }

  public double addedViolationPoints() {
    return vlAfter - vlBefore;
  }

  public double violationLevelBeforeViolation() {
    return vlBefore;
  }

  public double violationLevelAfterViolation() {
    return vlAfter;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  @Deprecated
  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }

  public void renew(Player punished, int legacyProtocolVersion, String modulename, String message, String details, double vlBefore, double vlAfter) {
    this.punished = punished;
    this.legacyProtocolVersion = legacyProtocolVersion;
    this.checkName = modulename;
    this.message = message;
    this.details = message;
    this.vlBefore = vlBefore;
    this.vlAfter = vlAfter;
    this.setCancelled(false);
  }

  @Override
  public void __INTERNAL__clearPlayerReference() {
    punished = null;
  }

  public static AsyncIntaveViolationEvent empty(IntavePlugin handle) {
    return construct(handle, null, 0, "error", "error", "error", 0, 0);
  }

  public static AsyncIntaveViolationEvent construct(IntavePlugin handle, Player punished, int legacyProtocolVersion, String modulename, String category, String message, int vlBefore, int vlAfter) {
    if(handle != IntavePlugin.singletonInstance()) {
      return null;
    }
    return new AsyncIntaveViolationEvent(punished, legacyProtocolVersion, modulename, category, message, vlBefore, vlAfter);
  }

  public enum MessageSpecifier {
    FULL,
    COMPACT
  }
}

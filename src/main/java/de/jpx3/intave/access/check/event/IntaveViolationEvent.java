package de.jpx3.intave.access.check.event;

import com.google.common.base.Preconditions;
import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.access.IntaveEvent;
import de.jpx3.intave.access.check.Check;
import de.jpx3.intave.access.player.PlayerAccess;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public final class IntaveViolationEvent extends IntaveEvent implements Cancellable {
  private Player punished;
  private String checkName;
  private String message;
  private String details;
  private double vlBefore;
  private double vlAfter;
  private Reaction reaction = Reaction.INTERRUPT_AND_REPORT;

  private IntaveViolationEvent() {
  }

  public void copy(Player punished, String checkName, String message, String details, double vlBefore, double vlAfter) {
    this.punished = punished;
    this.checkName = checkName;
    this.message = message;
    this.details = details;
    this.vlBefore = vlBefore;
    this.vlAfter = vlAfter;
    this.reaction = Reaction.INTERRUPT_AND_REPORT;
    this.setCancelled(false);
  }

  public Player player() {
    return punished;
  }

  public PlayerAccess playerAccess() {
    return IntaveAccessor.unsafeAccess().player(player());
  }

  public String check() {
    return checkName;
  }

  public String checkName() {
    return checkName;
  }

  public Check checkEnum() {
    return Check.fromString(checkName);
  }

  public String message() {
    if (details.isEmpty()) {
      return message;
    }
    return message + " (" + details.trim() + ")";
  }

  public String compactMessage() {
    return message;
  }

  public double addedViolationPoints() {
    return reducePrecision(vlAfter - vlBefore);
  }

  private final static double REDUCE_APPLIER = 1000d;

  private double reducePrecision(double input) {
    return Math.round(input * REDUCE_APPLIER) / REDUCE_APPLIER;
  }

  public double violationLevelBeforeViolation() {
    return vlBefore;
  }

  public double violationLevelAfterViolation() {
    return vlAfter;
  }

  @Override
  public boolean isCancelled() {
    return reaction != Reaction.INTERRUPT_AND_REPORT;
  }

  @Override
  @Deprecated
  public void setCancelled(boolean cancelled) {
    suggestReaction(cancelled ? Reaction.IGNORE : Reaction.INTERRUPT_AND_REPORT);
  }

  @Deprecated
  public void suggestReaction(Reaction reaction) {
    Preconditions.checkNotNull(reaction);
    this.reaction = reaction;
  }

  @Deprecated
  public Reaction reaction() {
    return reaction;
  }

  @Override
  public void referenceInvalidate() {
    punished = null;
  }

  @Override
  public String toString() {
    return "IntaveViolationEvent{" +
      "punished=" + punished +
      ", checkName='" + checkName + '\'' +
      ", message='" + message + '\'' +
      ", details='" + details + '\'' +
      ", vlBefore=" + vlBefore +
      ", vlAfter=" + vlAfter +
      ", reaction=" + reaction +
      '}';
  }

  public static IntaveViolationEvent empty() {
    return new IntaveViolationEvent();
  }

  public enum Reaction {
    IGNORE,
    INTERRUPT,
    INTERRUPT_AND_REPORT
  }
}

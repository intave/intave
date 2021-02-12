package de.jpx3.intave.access;

import com.google.common.base.Preconditions;
import de.jpx3.intave.IntaveAccessor;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * Created by Jpx3 on 10.11.2017.
 */

public final class IntaveViolationEvent extends AbstractIntaveExternalEvent implements Cancellable {
  private Player punished;
  private String checkName;
  private String message;
  private String details;
  private double vlBefore;
  private double vlAfter;
  private Reaction reaction = Reaction.INTERRUPT_AND_REPORT;

  private IntaveViolationEvent(
    Player punished,
    String checkName,
    String message,
    String details,
    double vlBefore,
    double vlAfter
  ) {
    this.punished = punished;
    this.checkName = checkName;
    this.message = message;
    this.details = details;
    this.vlBefore = vlBefore;
    this.vlAfter = vlAfter;
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

  public String message() {
    return message + " (" + details.trim() + ")";
  }

  public String compactMessage() {
    return message;
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
    return reaction != Reaction.INTERRUPT_AND_REPORT;
  }

  @Override
  @Deprecated
  public void setCancelled(boolean cancelled) {
    this.reaction = cancelled ? Reaction.IGNORE : Reaction.INTERRUPT_AND_REPORT;
  }

  public void suggestReaction(Reaction reaction) {
    Preconditions.checkNotNull(reaction);

    this.reaction = reaction;
  }

  public Reaction reaction() {
    return reaction;
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

  @Override
  public void refClear() {
    punished = null;
  }

  public static IntaveViolationEvent empty() {
    return construct(null, "error", "error", "error", 0, 0);
  }

  public static IntaveViolationEvent construct(Player punished, String checkName, String message, String details, int vlBefore, int vlAfter) {
    return new IntaveViolationEvent(punished, checkName, message, details, vlBefore, vlAfter);
  }

  public enum Reaction {
    IGNORE,
    INTERRUPT,
    INTERRUPT_AND_REPORT
  }
}

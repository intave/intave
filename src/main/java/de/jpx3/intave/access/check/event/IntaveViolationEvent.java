package de.jpx3.intave.access.check.event;

import com.google.common.base.Preconditions;
import de.jpx3.intave.IntaveAccessor;
import de.jpx3.intave.access.IntaveEvent;
import de.jpx3.intave.access.check.Check;
import de.jpx3.intave.access.player.PlayerAccess;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * An {@link IntaveViolationEvent} is called every time Intave concludes a thread/violation
 * by a specific check for a specific player with a specific message and details, adding a specific amount of VL
 */
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

  /**
   * Retrieve the detected player
   * @return the player detected
   */
  public Player player() {
    return punished;
  }

  /**
   * Retrieve the detected players {@link PlayerAccess}
   * @return the detected players {@link PlayerAccess}
   */
  public PlayerAccess playerAccess() {
    return IntaveAccessor.unsafeAccess().player(player());
  }

  @Deprecated
  public String check() {
    return checkName;
  }

  /**
   * Retrieve the executing checks name
   * @return the name of the executing check
   */
  public String checkName() {
    return checkName;
  }

  /**
   * Retrieve the executing checks corresponding {@link Check}
   * @return the executing checks corresponding {@link Check}
   */
  public Check checkEnum() {
    return Check.fromString(checkName);
  }

  /**
   * Retrieve a generalized detection message for the specific violation, when available
   * followed by violation details encapsulated in exclamation marks.
   * @return a generalized message for this violation with violation details in exclamation marks.
   */
  public String message() {
    if (details.isEmpty()) {
      return message;
    }
    return message + " (" + details.trim() + ")";
  }

  /**
   * Retrieve a generalized message for the specific violation.
   * Usually in a format where it can be prefixed with the players name, eg. "moved incorrectly"
   * @return a generalized message for this violation
   */
  public String compactMessage() {
    return message;
  }

  /**
   * Retrieve the amount of added violation points
   * @return the added violation points
   */
  public double addedViolationPoints() {
    return reducePrecision(vlAfter - vlBefore);
  }

  private final static double REDUCE_APPLIER = 1000d;

  private double reducePrecision(double input) {
    return Math.round(input * REDUCE_APPLIER) / REDUCE_APPLIER;
  }

  /**
   * Retrieve the violation level before the violation
   * @return the violation level before the violation
   */
  public double violationLevelBeforeViolation() {
    return vlBefore;
  }

  /**
   * Retrieve the violation level after the violation
   * @return the violation level after the violation
   */
  public double violationLevelAfterViolation() {
    return vlAfter;
  }

  /**
   * Retrieves whether the reaction has been altered
   * @return true if the reaction has been altered, false if not
   */
  @Override
  @Deprecated
  public boolean isCancelled() {
    return reaction != Reaction.INTERRUPT_AND_REPORT;
  }

  /**
   * Change whether we should ignore this violation
   * @param cancelled the new cancelled state
   */
  @Override
  @Deprecated
  public void setCancelled(boolean cancelled) {
    suggestReaction(cancelled ? Reaction.IGNORE : Reaction.INTERRUPT_AND_REPORT);
  }

  /**
   * Suggest a new reaction for this violation.
   * @see Reaction
   * @param reaction the suggested reaction
   */
  public void suggestReaction(Reaction reaction) {
    Preconditions.checkNotNull(reaction);
    this.reaction = reaction;
  }

  /**
   * Retrieve the reaction to perform.
   * This setting is only modified by the user and always INTERRUPT_AND_REPORT on violation.
   * @return the reaction to perform.
   */
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

  /**
   * A reaction setting is used to allow flexibility when modifying Intaves violation mitigation IG.
   * Select IGNORE to completely ignore the detected thread,
   * select INTERRUPT to block the thread without adding VL or outputing to the verbose channel.
   * select INTERRUPT_AND_REPORT to suggest adding vl and  - when Intave approves it - mitigating the action.
   */
  public enum Reaction {
    IGNORE,
    INTERRUPT,
    INTERRUPT_AND_REPORT
  }
}

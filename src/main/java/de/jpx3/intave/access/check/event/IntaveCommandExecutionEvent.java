package de.jpx3.intave.access.check.event;

import com.google.common.base.Preconditions;
import de.jpx3.intave.access.IntaveEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * An {@link IntaveCommandExecutionEvent} is called every time Intave executes a command
 * off a {@link IntaveViolationEvent} event
 */
public final class IntaveCommandExecutionEvent extends IntaveEvent implements Cancellable {
  private Player punished;
  private String command;
  private String check;
  private String violationMessage;
  private String violationDetails;
  private double activationVL;
  private boolean delayedExecution;
  private boolean cancelled;

  private IntaveCommandExecutionEvent() {
  }

  public void copy(
    Player punished,
    String command,
    String check,
    String violationMessage,
    String violationDetails,
    double activationVL,
    boolean delayedExecute
  ) {
    this.punished = punished;
    this.command = command;
    this.check = check;
    this.violationMessage = violationMessage;
    this.violationDetails = violationDetails;
    this.activationVL = activationVL;
    this.delayedExecution = delayedExecute;
    this.setCancelled(false);
  }

  /**
   * Retrieves the player a command is executed upon
   * @return the targeted player
   */
  public Player player() {
    return punished;
  }

  /**
   * Retrieves the executed command
   * @return the executed command
   */
  public String command() {
    return command;
  }

  /**
   * Overrides the executed command
   * @param command the new command to be executed
   */
  public void setCommand(String command) {
    Preconditions.checkNotNull(command);
    this.command = command;
  }

  /**
   * The name of check of the underlying violation
   * @return the check name
   */
  public String violationCheck() {
    return check;
  }

  /**
   * The message of the underlying violation
   * @return the violation message
   */
  public String violationMessage() {
    return violationMessage;
  }

  /**
   * The details of the underlying violation
   * @return the violation details
   */
  public String violationDetails() {
    return violationDetails;
  }

  /**
   * The activation violation level of the underlying violation
   * @return the violation activation level
   */
  public double activationVL() {
    return activationVL;
  }

  /**
   * Returns whether the command was executed with delay
   * @return true if the command was executed delayed, false if not
   */
  public boolean delayedExecute() {
    return delayedExecution;
  }

  /**
   * Returns whether the event should be cancelled
   * @return true when the event is cancelled, false when it is not
   */
  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  /**
   * Cancels or uncancels the event
   * @param cancelled the new cancel variant
   */
  @Override
  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }

  @Override
  public void referenceInvalidate() {
    punished = null;
  }

  @Override
  public String toString() {
    return "IntaveCommandExecutionEvent{" +
      "punished=" + punished +
      ", command='" + command + '\'' +
      ", check='" + check + '\'' +
      ", violationMessage='" + violationMessage + '\'' +
      ", violationDetails='" + violationDetails + '\'' +
      ", activationVL=" + activationVL +
      ", delayedExecution=" + delayedExecution +
      ", cancelled=" + cancelled +
      '}';
  }

  public static IntaveCommandExecutionEvent empty() {
    return new IntaveCommandExecutionEvent();
  }
}

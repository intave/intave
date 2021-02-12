package de.jpx3.intave.access;

import com.google.common.base.Preconditions;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * Created by Jpx3 on 10.11.2017.
 */

public final class IntaveCommandExecutionEvent extends AbstractIntaveExternalEvent implements Cancellable {
  private Player punished;
  private String command;
  private boolean delayedExecution;
  private boolean cancelled;

  private IntaveCommandExecutionEvent(Player punished, String command, boolean delayedExecution) {
    this.punished = punished;
    this.command = command;
    this.delayedExecution = delayedExecution;
    this.setCancelled(false);
  }

  public Player player() {
    return punished;
  }

  public String command() {
    return command;
  }

  public void setCommand(String command) {
    Preconditions.checkNotNull(command);

    this.command = command;
  }

  public boolean delayedExecute() {
    return delayedExecution;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }

  @Override
  public void refClear() {
    punished = null;
  }

  public void copy(Player punished, String command, boolean delayedExecute) {
    this.punished = punished;
    this.command = command;
    this.delayedExecution = delayedExecute;
    this.setCancelled(false);
  }

  public static IntaveCommandExecutionEvent empty() {
    return construct(null, "empty", false);
  }

  public static IntaveCommandExecutionEvent construct(Player punished, String command, boolean isWaveExecuted) {
    return new IntaveCommandExecutionEvent(punished, command, isWaveExecuted);
  }
}

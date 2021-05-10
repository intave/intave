package de.jpx3.intave.access.check.event;

import com.google.common.base.Preconditions;
import de.jpx3.intave.access.IntaveEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public final class IntaveCommandExecutionEvent extends IntaveEvent implements Cancellable {
  private Player punished;
  private String command;
  private boolean delayedExecution;
  private boolean cancelled;

  private IntaveCommandExecutionEvent() {
  }

  public void copy(Player punished, String command, boolean delayedExecute) {
    this.punished = punished;
    this.command = command;
    this.delayedExecution = delayedExecute;
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
  public void referenceInvalidate() {
    punished = null;
  }

  public static IntaveCommandExecutionEvent empty() {
    return new IntaveCommandExecutionEvent();
  }
}

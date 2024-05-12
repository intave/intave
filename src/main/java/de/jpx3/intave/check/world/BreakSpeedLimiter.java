package de.jpx3.intave.check.world;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.world.breakspeedlimiter.CompletionDurationCheck;
import de.jpx3.intave.check.world.breakspeedlimiter.RestartCheck;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;

public final class BreakSpeedLimiter extends Check {
  private final CheckViolationLevelDecrementer decrementer;

  public BreakSpeedLimiter(IntavePlugin plugin) {
    super("BreakSpeedLimiter", "breakspeedlimiter");
    setupParts();
    decrementer = new CheckViolationLevelDecrementer(this, 0.15);
    startDecrementTask();
  }

  private void startDecrementTask() {
    int taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(
      IntavePlugin.singletonInstance(),
      () -> UserRepository.applyOnAll(user -> decrementer.decrement(user, 0.05))
      , 40, 40);
    TaskTracker.begun(taskId);
  }

  public void setupParts() {
    appendCheckPart(new CompletionDurationCheck(this));
    appendCheckPart(new RestartCheck(this));
  }
}
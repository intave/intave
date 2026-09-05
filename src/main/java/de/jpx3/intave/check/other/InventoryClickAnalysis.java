package de.jpx3.intave.check.other;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.other.inventoryclickanalysis.*;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.user.UserRepository;

public final class InventoryClickAnalysis extends Check {
  public static final double MAX_VL_DECREMENT_PER_SECOND = 1;
  private final boolean highToleranceMode;
  private final CheckViolationLevelDecrementer decrementer;

  public InventoryClickAnalysis(IntavePlugin plugin) {
    super("InventoryClickAnalysis", "inventoryclickanalysis");
    decrementer = new CheckViolationLevelDecrementer(this, MAX_VL_DECREMENT_PER_SECOND);
    this.highToleranceMode = configuration().settings().boolBy("high-tolerance", true);
    this.startDecrementTask();
    this.setupCheckParts();
  }

  private void startDecrementTask() {
    Tasks.periodicNamed(
      "InventoryClickAnalysis.decrement",
      () -> UserRepository.applyOnOnlineUsers(user -> decrementer.decrement(user, 0.05)),
      40, 40
    ).startAsync();
  }

  private void setupCheckParts() {
    appendCheckPart(new OnMoveCheck(this));
    appendCheckPart(new NotOpenCheck(this));
    appendCheckPart(new DelayAnalyzer(this, highToleranceMode));
    appendCheckPart(new RegrDelayAnalyzer(this));
    appendCheckPart(new PacketDelayAnalyzer(this));
    appendCheckPart(new AutoTotem(this));
  }
}

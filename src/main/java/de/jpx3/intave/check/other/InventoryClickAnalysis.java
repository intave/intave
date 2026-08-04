package de.jpx3.intave.check.other;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.other.inventoryclickanalysis.*;
import de.jpx3.intave.check.world.interaction.PrinterMode;
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
    Tasks.periodicNamed("InventoryClickAnalysis.decrementer",() -> {
      UserRepository.applyOnAll(user -> decrementer.decrement(user, 0.05));
    }, 40, 40).startAsync();
  }

  private void setupCheckParts() {
    boolean printerMode = PrinterMode.enabled();

    if (!printerMode) {
      // A printer takes its blocks out of the inventory itself: it clicks while the player
      // walks, at machine speed and with machine regularity - which is exactly what these
      // three describe. They cannot be run against one without flagging it, and OnMoveCheck
      // additionally cancels the click and closes the inventory, so the printer never gets
      // its block. See PrinterMode for the trade.
      appendCheckPart(new OnMoveCheck(this));
      appendCheckPart(new DelayAnalyzer(this, highToleranceMode));
      appendCheckPart(new RegrDelayAnalyzer(this));
    }

    // Kept in printer mode: none of these judge how fast or how rhythmically items are
    // taken. PacketDelayAnalyzer only records timings, NotOpenCheck answers a client
    // clicking in an inventory it never opened, and auto-totem is unrelated to building.
    appendCheckPart(new NotOpenCheck(this));
    appendCheckPart(new PacketDelayAnalyzer(this));
    appendCheckPart(new AutoTotem(this));
  }
}
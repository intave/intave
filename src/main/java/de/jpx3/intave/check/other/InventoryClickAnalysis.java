package de.jpx3.intave.check.other;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.other.inventoryclickanalysis.*;

public final class InventoryClickAnalysis extends Check {
  public static final double MAX_VL_DECREMENT_PER_SECOND = 1;
  private final CheckViolationLevelDecrementer decrementer;
  private final boolean highToleranceMode;

  public InventoryClickAnalysis(IntavePlugin plugin) {
    super("InventoryClickAnalysis", "inventoryclickanalysis");
    decrementer = new CheckViolationLevelDecrementer(this, MAX_VL_DECREMENT_PER_SECOND);
    this.highToleranceMode = configuration().settings().boolBy("high-tolerance", true);
    this.setupCheckParts();
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
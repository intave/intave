package de.jpx3.intave.check.movement;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckConfiguration.CheckSettings;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.movement.timer.Balance;

public final class Timer extends Check {
  private final CheckViolationLevelDecrementer decrementer;

  private final boolean highToleranceMode;
  private final boolean reverseBlink;
  private final boolean reverseLag;
  private final boolean lowTolerance;
  private final boolean stutterPatch;
  private final Balance balance;

  public Timer() {
    super("Timer", "timer");
    this.decrementer = new CheckViolationLevelDecrementer(this, 0.2);
    CheckSettings settings = configuration().settings();
    highToleranceMode = settings.boolBy("high-tolerance", false);
    lowTolerance = settings.boolBy("low-tolerance", IntaveControl.GOMME_MODE);
    reverseBlink = settings.boolBy("reverse-blink", true);
    reverseLag = settings.boolBy("reverse-lag", IntaveControl.GOMME_MODE);
    stutterPatch = settings.boolBy("block-stutter-hits", false);

    if (highToleranceMode && lowTolerance) {
      IntaveLogger.logger().info("Conflicting tolerance settings: must either be high or low.");
    } else if (highToleranceMode) {
      IntaveLogger.logger().info("Enabled high ping tolerance");
    } else if (lowTolerance) {
      IntaveLogger.logger().info("Enabled low network tolerance mode");
    }
    if (stutterPatch) {
      IntaveLogger.logger().info("Enabled stutter hits prevention");
    }

    this.balance = new Balance(this);
    appendCheckPart(balance);
    //  appendCheckPart(new MovementFrequency(this));
  }

  public void receiveMovement(PacketEvent event) {
    balance.receiveMovement(event);
  }

  @Override
  public boolean enabled() {
    return true;
  }

  @Override
  public boolean performLinkage() {
    return true;
  }

  public boolean highToleranceMode() {
    return highToleranceMode;
  }

  public boolean lowToleranceMode() {
    return lowTolerance;
  }

  public boolean reverseBlink() {
    return reverseBlink;
  }

  public boolean reverseLag() {
    return reverseLag;
  }

  public boolean lowTolerance() {
    return lowTolerance;
  }

  public boolean stutterPatch() {
    return stutterPatch;
  }

  public CheckViolationLevelDecrementer decrementer() {
    return decrementer;
  }
}

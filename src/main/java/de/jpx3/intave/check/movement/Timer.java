package de.jpx3.intave.check.movement;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckConfiguration.CheckSettings;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.movement.timer.PlayerTime;

public final class Timer extends Check {
  private final CheckViolationLevelDecrementer decrementer;

  private final boolean highToleranceMode;
  private final boolean reverseBlink;
  private final boolean reverseLag;
  private final boolean lowTolerance;
  private final int blinkLimit;
  private final boolean detectPulseBlink;
  private final PlayerTime playerTime;

  public Timer() {
    super("Timer", "timer");
    this.decrementer = new CheckViolationLevelDecrementer(this, 0.2);
    CheckSettings settings = configuration().settings();

    reverseBlink = settings.boolBy("reverse-blink", true);

    // deprecated
    highToleranceMode = settings.boolBy("high-tolerance", false);
    lowTolerance = settings.boolBy("low-tolerance", IntaveControl.GOMME_MODE);
    reverseLag = settings.boolBy("reverse-lag", false);

    blinkLimit = settings.intBy("blink-limit", (lowTolerance ? 100 : -1));
    detectPulseBlink = settings.boolBy("block-pulse-blink", lowTolerance);

    this.playerTime = new PlayerTime(this);
    appendCheckPart(playerTime);
  }

  public void receiveMovement(PacketEvent event) {
    playerTime.receiveMovement(event);
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

  public int blinkLimit() {
    return blinkLimit;
  }

  public boolean detectPulseBlink() {
    return detectPulseBlink;
  }

  public CheckViolationLevelDecrementer decrementer() {
    return decrementer;
  }
}

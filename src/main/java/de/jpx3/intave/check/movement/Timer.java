package de.jpx3.intave.check.movement;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.CheckConfiguration.CheckSettings;
import de.jpx3.intave.check.CheckViolationLevelDecrementer;
import de.jpx3.intave.check.movement.timer.MicroBlink;
import de.jpx3.intave.check.movement.timer.PlayerTime;

public final class Timer extends Check {
  private final CheckViolationLevelDecrementer decrementer;

  private final boolean highToleranceMode;
  private final boolean reverseBlink;
  private final boolean reverseLag;
  private final boolean lowTolerance;
  private int blinkLimit;
  private final int timerTolerance;
  private final boolean detectPulseBlink;
  private final PlayerTime playerTime;
  private final MicroBlink microBlink;

  public Timer() {
    super("Timer", "timer");
    this.decrementer = new CheckViolationLevelDecrementer(this, 0.2);
    CheckSettings settings = configuration().settings();

    reverseBlink = settings.boolBy("reverse-blink", true);

    // deprecated
    highToleranceMode = settings.boolBy("high-tolerance", false);
    lowTolerance = settings.boolBy("low-tolerance", false);
    // reverse lag just sucks
    reverseLag = settings.boolBy("reverse-lag", false);

    blinkLimit = settings.intBy("blink-limit", (lowTolerance ? 100 : -1));
    if (blinkLimit < 60 && blinkLimit >= 0) {
      blinkLimit = 60;
    }
    timerTolerance = settings.intBy("tolerance", 1);
    detectPulseBlink = settings.boolBy("block-pulse-blink", lowTolerance);

    this.playerTime = new PlayerTime(this);
    appendCheckPart(playerTime);

    this.microBlink = new MicroBlink(this);
    appendCheckPart(microBlink);
  }

  public void receiveMovement(ProtocolPacketEvent event) {
    playerTime.receiveMovement(event);
    microBlink.receiveMovement(event);
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

  public int blinkLimit() {
    return blinkLimit;
  }

  public int timerTolerance() {
    return timerTolerance;
  }

  public boolean detectPulseBlink() {
    return detectPulseBlink;
  }

  public CheckViolationLevelDecrementer decrementer() {
    return decrementer;
  }
}

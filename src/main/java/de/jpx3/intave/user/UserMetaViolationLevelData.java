package de.jpx3.intave.user;

import de.jpx3.intave.access.TrustFactor;

public final class UserMetaViolationLevelData {
  public double physicsVL;
  public volatile boolean isInActiveTeleportBundle;

  public TrustFactor trustFactor = TrustFactor.YELLOW;
}
package de.jpx3.intave.player.fake;

import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedGameProfile;

public abstract class FakePlayerIdentity {
  private final int identifier;
  private final WrappedGameProfile profile;
  private final WrappedDataWatcher dataWatcher = new WrappedDataWatcher();

  protected FakePlayerIdentity(int identifier, WrappedGameProfile profile) {
    this.identifier = identifier;
    this.profile = profile;
  }

  public int identifier() {
    return identifier;
  }

  public WrappedGameProfile profile() {
    return profile;
  }

  public WrappedDataWatcher dataWatcher() {
    return dataWatcher;
  }
}
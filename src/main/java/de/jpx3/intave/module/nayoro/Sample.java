package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.shade.Position;

import java.util.Collections;
import java.util.List;

public final class Sample implements Environment {
  @Override
  public PlayerContainer mainPlayer() {
    return null;
  }

  @Override
  public List<Integer> entities() {
    return Collections.emptyList();
  }

  @Override
  public Position positionOf(int entity) {
    return null;
  }

  @Override
  public boolean property(String name) {
    return false;
  }

  @Override
  public long duration() {
    return 0;
  }

  @Override
  public long time() {
    return 0;
  }
}

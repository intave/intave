package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.Position;

import java.util.Map;
import java.util.Set;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2022
 */

public interface Environment {
  PlayerContainer mainPlayer();
  Set<Integer> entities();
  @Nullable
  Position positionOf(int entity);

  boolean inSight(int entity);

  boolean property(String name);
  long currentTime();

  Map<String, String> properties();
  boolean entityMoved(int entity, double distance);
}

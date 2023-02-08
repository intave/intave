package de.jpx3.intave.module.nayoro;

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
  Position positionOf(int entity);

  boolean inSight(int entity);

  boolean property(String name);
  boolean hasPassed(long time);

  Map<String, Boolean> properties();
  boolean entityMoved(int entity, double distance);
}

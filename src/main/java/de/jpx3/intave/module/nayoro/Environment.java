package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.shade.Position;

import java.util.List;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2022
 */

public interface Environment {
  PlayerContainer mainPlayer();
  List<Integer> entities();
  Position positionOf(int entity);

  boolean property(String name);

  long duration();
  long time();
}

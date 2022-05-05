package de.jpx3.intave.module.linker.nayoro;

import de.jpx3.intave.module.nayoro.Event;
import de.jpx3.intave.module.nayoro.PlayerContainer;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2022
 */

public interface NayoroEventExecutor {
  void execute(NayoroEventSubscriber subscriber, PlayerContainer player, Event event);
}

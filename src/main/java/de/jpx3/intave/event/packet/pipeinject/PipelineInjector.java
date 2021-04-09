package de.jpx3.intave.event.packet.pipeinject;

import org.bukkit.entity.Player;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public interface PipelineInjector {
  void inject(Player target);

  void uninject(Player target);
}

package de.jpx3.intave.player.fake.action;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.player.fake.FakePlayer;
import org.bukkit.entity.Player;

public abstract class Action {
  protected final Player observer;
  protected final FakePlayer fakePlayer;
  private final Probability probability;
  private int loop = 0;

  public Action(
    Probability probability,
    Player player,
    FakePlayer fakePlayer
  ) {
    this.probability = probability;
    this.observer = player;
    this.fakePlayer = fakePlayer;
  }

  public final void tryPerform() {
    if (++loop % this.probability.randomProbability() == 0) {
      Synchronizer.synchronize(this::perform);
    } else {
      Synchronizer.synchronize(this::performMissed);
    }
  }

  public abstract void perform();

  public void performMissed() {
  }

  protected void send(PacketWrapper<?> packet) {
    PacketEvents.getAPI().getPlayerManager().sendPacket(observer, packet);
  }
}

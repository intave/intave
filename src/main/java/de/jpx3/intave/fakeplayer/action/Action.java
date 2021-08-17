package de.jpx3.intave.fakeplayer.action;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.fakeplayer.FakePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

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

  public void performMissed() {}

  protected PacketContainer create(PacketType packetType) {
    return ProtocolLibrary.getProtocolManager().createPacket(packetType);
  }

  protected void send(PacketContainer packet) {
    try {
      ProtocolLibrary.getProtocolManager().sendServerPacket(observer, packet);
    } catch (InvocationTargetException exception) {
      exception.printStackTrace();
    }
  }
}
package de.jpx3.intave.player.fake.action;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.player.fake.FakePlayer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
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
    User user = UserRepository.userOf(observer);
    if (++loop % this.probability.randomProbability() == 0) {
      Synchronizer.synchronize(user, this::perform);
    } else {
      Synchronizer.synchronize(user, this::performMissed);
    }
  }

  public abstract void perform();

  public void performMissed() {
  }

  protected PacketContainer create(PacketType packetType) {
    return ProtocolLibrary.getProtocolManager().createPacket(packetType);
  }

  protected void send(PacketContainer packet) {
    PacketSender.sendServerPacket(observer, packet);
  }
}

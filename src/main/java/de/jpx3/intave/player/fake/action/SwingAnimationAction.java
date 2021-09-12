package de.jpx3.intave.player.fake.action;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.player.fake.FakePlayer;
import org.bukkit.entity.Player;

public final class SwingAnimationAction extends Action {
  private final static byte SWING_ANIMATION = 0;

  public SwingAnimationAction(Player player, FakePlayer fakePlayer) {
    super(Probability.HIGH, player, fakePlayer);
  }

  @Override
  public void perform() {
    PacketContainer packet = create(PacketType.Play.Server.ANIMATION);
    packet.getIntegers().writeSafely(0, this.fakePlayer.identifier());
    packet.getBytes().writeSafely(0, SWING_ANIMATION);
    send(packet);
  }
}

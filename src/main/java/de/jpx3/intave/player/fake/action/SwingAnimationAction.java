package de.jpx3.intave.player.fake.action;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import de.jpx3.intave.player.fake.FakePlayer;
import org.bukkit.entity.Player;

public final class SwingAnimationAction extends Action {
  public SwingAnimationAction(Player player, FakePlayer fakePlayer) {
    super(Probability.HIGH, player, fakePlayer);
  }

  @Override
  public void perform() {
    send(new WrapperPlayServerEntityAnimation(
      this.fakePlayer.identifier(),
      WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM
    ));
  }
}

package de.jpx3.intave.check.combat.heuristics.detect.testing;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.nayoro.NayoroRelay;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.nayoro.ClickEvent;
import de.jpx3.intave.module.nayoro.PlayerContainer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import java.util.UUID;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_DIG;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;

public final class TestingHeuristic extends MetaCheckPart<Heuristics, TestingHeuristic.ExampleMeta> {
  public TestingHeuristic(Heuristics parent) {
    super(parent, ExampleMeta.class);
  }

  @NayoroRelay
  public void on(PlayerContainer player, ClickEvent event) {
    ExampleMeta meta = player.meta(ExampleMeta.class);
    player.debug("This is a test, " + meta.uniqueId);
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE, BLOCK_DIG
    }
  )
  public void receiveInteractionPacket(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    ExampleMeta exampleMeta = metaOf(user);
  }

  public static class ExampleMeta extends CheckCustomMetadata {
    long uniqueId = UUID.randomUUID().getLeastSignificantBits();
  }
}

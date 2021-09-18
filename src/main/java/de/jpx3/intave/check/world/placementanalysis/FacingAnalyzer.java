package de.jpx3.intave.check.world.placementanalysis;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.world.PlacementAnalysis.COMMON_FLAG_MESSAGE;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.BLOCK_PLACE;

public final class FacingAnalyzer extends CheckPart<PlacementAnalysis> {
  private final IntavePlugin plugin;

  public FacingAnalyzer(PlacementAnalysis parentCheck) {
    super(parentCheck);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    packetsIn = {
      BLOCK_PLACE
    }
  )
  public void checkPlacementVector(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    PacketContainer packet = event.getPacket();
    if (blockingPlacementPacket(packet)) {
      return;
    }
    StructureModifier<Float> floatStructureModifier = packet.getFloat();
    if (floatStructureModifier.size() < 3) {
      return;
    }
    float f1 = floatStructureModifier.read(0);
    float f2 = floatStructureModifier.read(1);
    float f3 = floatStructureModifier.read(2);
    if (f1 < 0 || f2 < 0 || f3 < 0 || f1 > 1 || f2 > 1 || f3 > 1) {
      Violation violation = Violation.builderFor(PlacementAnalysis.class)
        .forPlayer(player)
        .withMessage(COMMON_FLAG_MESSAGE)
        .withVL(5)
        .build();
      Modules.violationProcessor().processViolation(violation);
      //dmc14
      user.applyAttackNerfer(AttackNerfStrategy.CANCEL_FIRST_HIT, "14");
      user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "14");
    }
  }

  private boolean blockingPlacementPacket(PacketContainer packet) {
    Integer integer = packet.getIntegers().readSafely(0);
    return integer != null && integer == 255;
  }
}

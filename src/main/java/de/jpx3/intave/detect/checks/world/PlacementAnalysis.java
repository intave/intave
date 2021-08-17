package de.jpx3.intave.detect.checks.world;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.detect.Check;
import de.jpx3.intave.detect.checks.world.placementanalysis.*;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.ProtocolMetadata;

public final class PlacementAnalysis extends Check {
  private final IntavePlugin plugin;
  public final static String COMMON_FLAG_MESSAGE = "suspicious block-placement";

  public PlacementAnalysis(IntavePlugin plugin) {
    super("PlacementAnalysis", "placementanalysis");
    this.plugin = plugin;
    this.setupSubChecks();
  }

  @Native
  public void setupSubChecks() {
    boolean enterprise = (ProtocolMetadata.VERSION_DETAILS & 0x200) != 0;
    boolean partner = (ProtocolMetadata.VERSION_DETAILS & 0x100) != 0;
    if (enterprise || partner) {
      appendCheckPart(new SpeedAnalyzer(this));
      appendCheckPart(new RotationSpeedAnalyzer(this));
      appendCheckPart(new SharpRotationAnalyzer(this));
      appendCheckPart(new SneakAnalyzer(this));
    }
    appendCheckPart(new PacketOrderAnalyzer(this));
    appendCheckPart(new FacingAnalyzer(this));
  }

  public void applyPlacementAnalysisDamageCancel(User user, String checkId) {
    user.applyAttackNerfer(AttackNerfStrategy.CANCEL_FIRST_HIT, checkId);
    user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, checkId);
  }
}
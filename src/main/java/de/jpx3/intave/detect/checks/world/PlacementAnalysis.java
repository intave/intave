package de.jpx3.intave.detect.checks.world;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.IntaveCheck;
import de.jpx3.intave.detect.checks.world.placementanalysis.PlacementFacingAnalyzer;
import de.jpx3.intave.detect.checks.world.placementanalysis.PlacementPacketOrderAnalyzer;
import de.jpx3.intave.detect.checks.world.placementanalysis.PlacementRotationSpeedAnalyzer;
import de.jpx3.intave.detect.checks.world.placementanalysis.PlacementSpeedAnalyzer;
import de.jpx3.intave.event.violation.AttackNerfStrategy;
import de.jpx3.intave.tools.annotate.Native;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaClientData;

public final class PlacementAnalysis extends IntaveCheck {
  private final IntavePlugin plugin;
  public final static String COMMON_FLAG_MESSAGE = "suspicious block-placement";

  public PlacementAnalysis(IntavePlugin plugin) {
    super("PlacementAnalysis", "placementanalysis");
    this.plugin = plugin;
    this.setupSubChecks();
  }

  @Native
  public void setupSubChecks() {
    boolean enterprise = (UserMetaClientData.VERSION_DETAILS & 0x200) != 0;
    boolean partner = (UserMetaClientData.VERSION_DETAILS & 0x100) != 0;
    if(enterprise || partner) {
      appendCheckPart(new PlacementSpeedAnalyzer(this));
      appendCheckPart(new PlacementRotationSpeedAnalyzer(this));
    }
    appendCheckPart(new PlacementPacketOrderAnalyzer(this));
    appendCheckPart(new PlacementFacingAnalyzer(this));
  }

  public void applyPlacementAnalysisDamageCancel(User user, String checkId) {
    user.applyAttackNerfer(AttackNerfStrategy.CANCEL_FIRST_HIT, checkId);
    user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, checkId);
  }
}
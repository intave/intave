package de.jpx3.intave.check.world.interaction;

import de.jpx3.intave.config.ConfiguredFlag;

/**
 * "Printer mode": the placement side of Intave is reduced to what a vanilla server
 * itself enforces, so schematic helpers (Litematica's printer and easy-place mode,
 * build clients) can place blocks the way they do.
 * <p>
 * What it covers, all of it placement-only:
 * <ul>
 *   <li>the interaction ray trace no longer has to reproduce a placement — a placement
 *       against nothing ("airplace"), against a block the player is not looking at, with
 *       a block face or a hit vector the player's own view does not produce (that hit
 *       vector is exactly what easy-place doctors to pick a block state) is forwarded to
 *       the server as the client sent it instead of being dropped;</li>
 *   <li>no placement violation is raised for those, so nothing accumulates towards a
 *       kick or a ban and nothing shows up in verbose;</li>
 *   <li>{@link BlockTrustChain} and the post-detection placement deny window no longer
 *       reject placements, so a fast chain of blocks placed against blocks that are
 *       themselves still unconfirmed goes through;</li>
 *   <li>the scaffold analysis (rotation speed/snap/flick, facing, timings) is not run at
 *       all, since a printer places without looking at the block;</li>
 *   <li>the combat rotation-snap heuristic no longer treats a snap next to an <i>arm
 *       swing</i> as suspicious (only one next to an actual attack packet), because
 *       turning towards a block, swinging and turning back is what a printer does;</li>
 *   <li>the inventory-click checks that judge how items are taken — while walking, how
 *       quickly and how regularly — are not run, since the printer pulls its blocks out
 *       of the inventory itself, and the walking one cancels the click outright.</li>
 * </ul>
 * Breaking blocks, interacting, reach, movement and combat itself are untouched — this
 * gives up scaffold detection and inventory-click timing analysis, which is the trade a
 * build server makes knowingly.
 * <p>
 * Where the value is read from — and why that is not simply "advanced.yml" — is described
 * on {@link ConfiguredFlag}. {@link #describe()} reports the result (and where it came
 * from) to {@code /iac diagnostics environment}.
 */
public final class PrinterMode {
  public static final String PATH = "check.placementanalysis.printer-mode";

  private static final ConfiguredFlag FLAG = new ConfiguredFlag(PATH, false);

  private PrinterMode() {
  }

  public static boolean enabled() {
    return FLAG.enabled();
  }

  /**
   * Human-readable state for the diagnostics command, so an owner can tell "the option is
   * off" apart from "the option never reached Intave" without reading the source.
   */
  public static String describe() {
    return FLAG.describe();
  }
}

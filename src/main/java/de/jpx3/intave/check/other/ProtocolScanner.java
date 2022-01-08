package de.jpx3.intave.check.other;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.other.protocolscanner.InvalidPitch;
import de.jpx3.intave.check.other.protocolscanner.SentSlotTwice;
import de.jpx3.intave.check.other.protocolscanner.SkinBlinker;

public final class ProtocolScanner extends Check {
  private final IntavePlugin plugin;

  public ProtocolScanner(IntavePlugin plugin) {
    super("ProtocolScanner", "protocolscanner");
    this.plugin = plugin;

    appendCheckParts(
      new SentSlotTwice(this),
      new InvalidPitch(this),
      new SkinBlinker(this)
    );
  }
}
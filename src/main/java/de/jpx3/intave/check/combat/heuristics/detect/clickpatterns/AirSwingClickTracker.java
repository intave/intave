package de.jpx3.intave.check.combat.heuristics.detect.clickpatterns;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public abstract class AirSwingClickTracker extends MetaCheckPart<Heuristics, AirSwingClickTracker.AirSwingClickTrackerMeta> {
  private final int sampleSize;
  // Could use bit-shift operations for these options in constructor? they're always true & false for now
  private boolean ignoreDoubleClicks = true;
  // This could be done inside the Anomaly system with REQUIRES_HEAVY_COMBAT, but doing it here allow us to just
  // pause sample instead of voiding them and also enter specific conditions
  private boolean requireCombat = false;

  public AirSwingClickTracker(Heuristics parentCheck, int sampleSize) {
    super(parentCheck, AirSwingClickTracker.AirSwingClickTrackerMeta.class);
    this.sampleSize = sampleSize;
  }

  public static class AirSwingClickTrackerMeta extends CheckCustomMetadata {
    private final List<Integer> delays = new ArrayList<>();
    private int delay;
    private int lastAttack; // In client ticks
    private boolean placedBlock;
  }

  // This isn't going to work because when we will extend it we won't know our user?
  // Should we put it through parameters or something?
  public abstract void check(List<Integer> delays);

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void clientSwing(PacketEvent event) {
    User user = userOf(event.getPlayer());
    AirSwingClickTrackerMeta meta = metaOf(user);
    // Completely ignore this swing, like it never existed!
    if (user.meta().attack().inBreakProcess || meta.placedBlock) {
      return;
    }

    boolean requireCombatCheck = !requireCombat || meta.lastAttack <= 10;
    boolean ignoreDoubleClickCheck = !ignoreDoubleClicks || meta.delay > 0;
    if (meta.delay <= 15 && requireCombatCheck && ignoreDoubleClickCheck) {
      meta.delays.add(meta.delay);
      if (meta.delays.size() == sampleSize) {
        check(meta.delays);
        meta.delays.clear();
      }
    }
    meta.delay = 0;
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      BLOCK_PLACE
    }
  )
  // BLOCK_PLACE is replaced by USE_ITEM in 1.9+ but it doesn't matter to us since those detections are for 1.8-
  public void clientBlockPlace(PacketEvent event) {
    User user = userOf(event.getPlayer());
    AirSwingClickTrackerMeta meta = metaOf(user);

    int blockPlaceDirection = event.getPacket().getIntegers().read(0);
    if (blockPlaceDirection != 255) {
      meta.placedBlock = true;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void clientUseEntity(PacketEvent event) {
    User user = userOf(event.getPlayer());
    AirSwingClickTrackerMeta meta = metaOf(user);
    PacketContainer packet = event.getPacket();
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      meta.lastAttack = 0;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void clientTickUpdate(PacketEvent event) {
    User user = userOf(event.getPlayer());
    AirSwingClickTrackerMeta meta = metaOf(user);
    meta.delay++;
    meta.lastAttack++;
    meta.placedBlock = false;
  }
}

package de.jpx3.intave.check.movement.timer;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.annotate.DispatchTarget;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.math.ContingencyTable;
import de.jpx3.intave.math.Histogram;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ConnectionMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.event.Cancellable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.comphenix.protocol.wrappers.EnumWrappers.EntityUseAction.ATTACK;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.USE_ENTITY;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.*;

public class MicroBlink extends MetaCheckPart<Timer, MicroBlink.MicroBlinkMeta> {
  public MicroBlink(Timer parentCheck) {
    super(parentCheck, MicroBlinkMeta.class);
    onEnable();
  }

  @PacketSubscription(
    packetsIn = USE_ENTITY
  )
  public void receiveUseEntity(
    User user, EntityUseReader reader, Cancellable cancellable
  ) {
    if (reader.useAction() == ATTACK) {
      MicroBlinkMeta meta = metaOf(user);
      meta.lastAttack = System.currentTimeMillis();
    }
  }

  private void onEnable() {
//    Bukkit.getScheduler().scheduleSyncRepeatingTask(IntavePlugin.singletonInstance(), () -> {
//      UserRepository.applyOnAll(user -> {
//        MicroBlinkMeta meta = metaOf(user);
////        if (meta.movementsPendingToTick.get() > 0) {
////        }
//        long old = meta.movementsPendingToTick.getAndSet(0);
////        if (old > 1) {
////        }
////        user.player().sendMessage(old + " movements per tick");
//      });
//    },1,1);
  }

  @DispatchTarget
  public void receiveMovement(ProtocolPacketEvent event) {
    User user = userOf(event.getPlayer());
    MicroBlinkMeta meta = metaOf(user);
    MovementMetadata movement = user.meta().movement();
    double horizontalDistance = movement.motion().horizontalLength();
    long pastAttack = System.currentTimeMillis() - meta.lastAttack;

    Histogram timeHistogram = meta.timeHistogram;

    meta.debugTickCounter++;
    meta.movementsPendingToTick.incrementAndGet();

    if (horizontalDistance > 0.125 && meta.lastHorizontalDistance > 0.125) {
      long timeDifference = System.currentTimeMillis() - meta.lastMovement;
      timeHistogram.add(timeDifference);
      double probability = timeHistogram.normalProbability(timeDifference);
//      if (!user.meta().protocol().combatUpdate()) {
//        System.out.println(timeDifference + "ms: " + probability + ", aligned: " + lagEntityAligned(user));
        long limit = (long) (50L + user.meta().connection().feedbackDelays.standardDeviation());
        if (limit < 70) {
          limit = 70;
        }
        boolean lagging = timeDifference > limit;
        boolean nearEntity = false;//lagEntityAligned(user);

        for (Double value : distancesToEntities(user).values()) {
          if (value < 3.0) {
            nearEntity = true;
            break;
          }
        }

        if (timeDifference > 70) {
//          System.out.println(timeDifference + "/" + limit + "ms, aligned: " + nearEntity);
        }

        ContingencyTable table = meta.lagToCombatTable;
        if (lagging && pastAttack < 1000) {
//          System.out.println("Lagging: " + timeDifference + "ms, near entity: " + nearEntity);
//          user.player().sendMessage("Lagging: " + timeDifference + "/"+limit+"ms, near entity: " + nearEntity);
          if (table.chi2() > 15) {
            Map<String, String> granular = new HashMap<>();
            granular.put("delay", String.valueOf(timeDifference));
            granular.put("tolerance", String.valueOf(limit));
            granular.put("chi2", String.valueOf(table.chi2()));
            Violation violation = Violation.builderFor(Timer.class)
              .forPlayer(user.player())
              .withCustomThreshold("microblink")
              .withGranulars(granular)
              .withMessage("seems be micro-lagging entity-aligned")
              .withDetails("chi2-test failed for \"near-entity\" and \"lagging\"")
              .withVL(3)
              .build();
            Modules.violationProcessor().processViolation(violation);
//            user.nerf(CANCEL, "TBX3");
          }
        }

        table.increment(lagging ? 0 : 1, nearEntity ? 0 : 1);

        if (meta.debugTickCounter % 50 == 0) {
//          System.out.println("Lagging: " + lagging + ", near entity: " + nearEntity);
//          System.out.println(table);
//          user.player().sendMessage((table.chi2pValue()) + "% cheating");
//          user.player().sendMessage("P(Lag) = " + formatDouble(table.probabilityOf(0) * 100, 2) + "%");
//          user.player().sendMessage("P(Lag | Entity) = " + formatDouble(table.conditionalProbabilityOf(0, 0) * 100, 2) + "%");
//
//
//          double chiProbability = table.chi2pValue();
//          user.player().sendMessage("Chi2: " + formatDouble(table.chi2(), 2));
//          long dataRemapping = table.get(0,0) / 20;
//          if (dataRemapping > 1) {
//            dataRemapping = 1;
//          }
//          double needDataPrior = Math.exp((dataRemapping * 4) - 4);
//
//          user.player().sendMessage("Cheat: " + formatDouble(needDataPrior * chiProbability * 100, 2) + "%");

        }

        if (timeDifference > 70 && lagEntityAligned(user) && probability < 0.000001) {
          meta.violationLevel += 1.5;
//          System.out.println("VL: " + meta.violationLevel);
          if (meta.violationLevel > 5) {
            Map<String, String> granular = new HashMap<>();
            granular.put("delay", String.valueOf(timeDifference));
            granular.put("tolerance", String.valueOf(limit));
            granular.put("internalvl", meta.violationLevel + "/" + meta.mitigationLevel);
            distancesToEntities(user).forEach((s, aDouble) -> {
              granular.put("distance_to_" + s, String.valueOf(aDouble));
            });
            Violation violation = Violation.builderFor(Timer.class)
              .forPlayer(user.player())
              .withCustomThreshold("microblink")
              .withGranulars(granular)
              .withMessage("seems be micro-lagging entity-aligned")
              .withDetails(formatDouble(probability * 100, 6) + "% likelihood of " + timeDifference + "ms")
              .withVL(meta.violationLevel - 5)
              .build();
            Modules.violationProcessor().processViolation(violation);
            meta.mitigationLevel += 2;
            if (meta.violationLevel > 10) {
              meta.violationLevel = Math.min(12, meta.violationLevel);
              if (meta.mitigationLevel > 8) {
                user.nerf(CANCEL, "TBX2");
                user.nerf(DMG_MEDIUM, "TBX2");
                user.nerf(DMG_ARMOR_INEFFECTIVE, "TBX2");
                user.nerf(CRITICALS, "TBX2");
              }
            }
            if (meta.mitigationLevel > 8) {
              user.nerf(SHORT_CANCEL, "TBX2");
            }
          }
        }
//      }

      if (probability < 0.000001 && timeDifference > 150 && timeDifference < 400 && pastAttack < 1250 && movement.lastTeleport > 5) {
        if (++meta.violationLevel > 5) {
          Map<String, String> granular = new HashMap<>();
          granular.put("delay", String.valueOf(timeDifference));
          granular.put("pastattack", String.valueOf(pastAttack));
          granular.put("internalvl", formatDouble(meta.violationLevel,2) + "/" + formatDouble(meta.mitigationLevel,2));
          Violation violation = Violation.builderFor(Timer.class)
            .forPlayer(user.player())
            .withCustomThreshold("microblink")
            .withGranulars(granular)
            .withMessage("seems to be micro-lagging in combat")
            .withDetails(formatDouble(probability * 100, 6) + "% likelihood of " + timeDifference + "ms")
            .withVL(meta.violationLevel - 5)
            .build();
          meta.mitigationLevel++;
          Modules.violationProcessor().processViolation(violation);
          if (meta.violationLevel > 10) {
            meta.violationLevel = Math.min(12, meta.violationLevel);
            if (meta.mitigationLevel > 15) {
              user.nerf(CANCEL, "TBX1");
              user.nerf(DMG_MEDIUM, "TBX1");
              user.nerf(DMG_ARMOR_INEFFECTIVE, "TBX1");
              user.nerf(CRITICALS, "TBX1");
            }
          }
          if (meta.mitigationLevel > 15) {
            user.nerf(SHORT_CANCEL, "TBX1");
          }
        }
      } else {
        meta.violationLevel = Math.max(0, meta.violationLevel - 0.007);
      }
    }

    meta.lastMovement = System.currentTimeMillis();
    meta.lastHorizontalDistance = horizontalDistance;
  }

  private boolean lagEntityAligned(User user) {
    MovementMetadata movement = user.meta().movement();
    ConnectionMetadata connection = user.meta().connection();
    Position lastPosition = movement.lastPosition();
    Position position = movement.position();
    for (Entity tracedEntity : connection.tracedEntities()) {
      Position entityPosition = tracedEntity.position.toPosition();
      // 1. position must be closer than last position
      double distanceToCurrentPos = position.distance(entityPosition);
      double distanceToLastPos = lastPosition.distance(entityPosition);
      if (distanceToCurrentPos >= distanceToLastPos) {
        continue;
      }
//      System.out.println("Distance to last " + distanceToLastPos);
      // 2. last position must be > 3 blocks away from entity, but not farther than 4 blocks
//      System.out.println("Distance to last " + distanceToLastPos);
      if (distanceToLastPos < 2.7 || distanceToLastPos > 3.8) {
        continue;
      }
      return true;
    }
//    System.out.println("Not near entity");
    return false;
  }

  private Map<String, Double> distancesToEntities(User user) {
    MovementMetadata movement = user.meta().movement();
    ConnectionMetadata connection = user.meta().connection();
    Position position = movement.position();
    Map<String, Double> distances = new HashMap<>();
    for (Entity tracedEntity : connection.tracedEntities()) {
      Position entityPosition = tracedEntity.position.toPosition();
      double distanceToCurrentPos = position.distance(entityPosition);
      double distanceToLastPos = movement.lastPosition().distance(entityPosition);
      if (distanceToCurrentPos >= distanceToLastPos) {
        continue;
      }
      distances.put(tracedEntity.entityName(), distanceToLastPos);
    }
    return distances;
  }

  public static class MicroBlinkMeta extends CheckCustomMetadata {
    private long lastMovement = 0L;
    private double lastHorizontalDistance = 0.0;
    private final Histogram timeHistogram = new Histogram(0, 500, 10, 20 * 60 * 2);
    private final ContingencyTable lagToCombatTable = new ContingencyTable(2, 2);
    private double violationLevel = 0.0;
    private double mitigationLevel = 0.0;

    private int debugTickCounter = 0;

    private long lastAttack = 0L;
    private AtomicLong movementsPendingToTick = new AtomicLong(0);
  }
}

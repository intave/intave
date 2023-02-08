package de.jpx3.intave.check.combat.heuristics.detect.experimental;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.util.concurrent.AtomicDouble;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.Blueprint;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.ClientMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import java.util.List;

import static de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionBlueprintMeta.RotationData;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

@Reserved
public abstract class RotationPrevisionBlueprint<M extends RotationPrevisionBlueprintMeta>
  extends Blueprint<Heuristics, RotationPrevisionBlueprintMeta, M> {
  private final int sampleSize;
  private final boolean lenientCombat;

  // Could use bit-shift operations for these options in constructor?
  public RotationPrevisionBlueprint(Heuristics parentCheck, Class<M> metaClass, int sampleSize) {
    super(parentCheck, metaClass);
    this.sampleSize = sampleSize;
    this.lenientCombat = false;
  }

  public RotationPrevisionBlueprint(Heuristics parentCheck, Class<M> metaClass, int sampleSize, boolean lenientCombat) {
    super(parentCheck, metaClass);
    this.sampleSize = sampleSize;
    this.lenientCombat = lenientCombat;
  }

  public abstract void check(User user, List<RotationData> rotationValues);

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY
    }
  )
  public void clientUseEntity(PacketEvent event) {
    User user = userOf(event.getPlayer());
    RotationPrevisionBlueprintMeta meta = metaOf(user);
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
    RotationPrevisionBlueprintMeta meta = metaOf(user);
    if (!user.meta().protocol().flyingPacketsAreSent()) {
      return;
    }
    meta.lastAttack++;
    Entity target = user.meta().attack().lastAttackedEntity();
    if (target == null) {
      return;
    }
    AttackMetadata attackData = user.meta().attack();
    MovementMetadata movementData = user.meta().movement();
    if (movementData.awaitTeleport || movementData.isInVehicle()) {
      return;
    }

    boolean moving = Hypot.fast(movementData.motionX(), movementData.motionZ()) + Math.abs(movementData.motionY()) >= 0.025;
    boolean combatRequirements = moving
      && meta.lastAttack <= (lenientCombat ? 3 : 1)
      && target.moving(0.025)
      && target.ticksAlive > 25
      && attackData.lastReach() > 1.0;

    if (!combatRequirements) {
      return;
    }

    RotationData rotationData = createRotationData(movementData, target);
    if (rotationData.yawDelta > 0.1 || rotationData.pitchDelta > 0.1) {
      meta.rotationValues.add(rotationData);
      if (meta.rotationValues.size() >= sampleSize) {
        check(user, meta.rotationValues);
        meta.rotationValues.clear();
      }
    }
  }

  public double determinationCoefficientYaw(List<RotationData> rotationValues) {
    double sum_X = 0, sum_Y = 0, sum_XY = 0;
    double squareSum_X = 0, squareSum_Y = 0;
    int size = rotationValues.size();

    for (RotationData rotationValue : rotationValues) {
      sum_X += rotationValue.expectedYawDelta;
      sum_Y += rotationValue.yawDelta;

      sum_XY += rotationValue.expectedYawDelta * rotationValue.yawDelta;

      squareSum_X += rotationValue.expectedYawDelta * rotationValue.expectedYawDelta;
      squareSum_Y += rotationValue.yawDelta * rotationValue.yawDelta;
    }

    double corr = (size * sum_XY - sum_X * sum_Y) /
      (Math.sqrt((size * squareSum_X -
        sum_X * sum_X) * (size * squareSum_Y -
        sum_Y * sum_Y)));

    return corr * corr;
  }

  public double average(List<Float> values) {
    return values.stream()
      .mapToDouble(Number::doubleValue)
      .average()
      .orElse(0D);
  }

  public double standardDeviation(List<Float> values) {
    double average = average(values);
    AtomicDouble variance = new AtomicDouble(0D);
    values.forEach(delay -> variance.getAndAdd(Math.pow(delay.doubleValue() - average, 2D)));
    return Math.sqrt(variance.get() / values.size());
  }

  private RotationData createRotationData(MovementMetadata movementData, Entity target) {
    float lastPlayerYaw = ClientMathHelper.wrapAngleTo180_float(movementData.lastRotationYaw);
    float yaw = ClientMathHelper.wrapAngleTo180_float(movementData.rotationYaw);
    float yawDelta = MathHelper.noAbsDistanceInDegrees(yaw, lastPlayerYaw);

    float pitchDelta = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);

    float expectedYaw = expectedYaw(target.position, movementData.lastPositionX, movementData.lastPositionZ);
    float expectedPitch = expectedPitch(target.position, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);

    float expectedYawDelta = MathHelper.noAbsDistanceInDegrees(expectedYaw, lastPlayerYaw);
    float expectPitchDelta = Math.abs(expectedPitch - movementData.rotationPitch);

    return new RotationData.RotationDataBuilder()
      .yaw(yaw)
      .yawDelta(yawDelta)
      .expectedYawDelta(expectedYawDelta)
      .expectedYaw(expectedYaw)
      .pitch(movementData.rotationPitch)
      .pitchDelta(pitchDelta)
      .expectedPitchDelta(expectPitchDelta)
      .expectedPitch(expectedPitch)
      .build();
  }

  private float expectedYaw(Entity.EntityPositionContext entityPositions, double posX, double posZ) {
    double diffX = entityPositions.posX - posX;
    double diffZ = entityPositions.posZ - posZ;
    return (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
  }

  private static float expectedPitch(Entity.EntityPositionContext entityPositions, double posX, double posY, double posZ) {
    double diffY = entityPositions.posY + 1.62f - (posY + 1.62f);
    double diffX = entityPositions.posX - posX;
    double diffZ = entityPositions.posZ - posZ;
    double d3 = Math.sqrt(diffX * diffX + diffZ * diffZ);
    return (float) (-Math.atan2(diffY, d3) * 180.0 / Math.PI);
  }
}


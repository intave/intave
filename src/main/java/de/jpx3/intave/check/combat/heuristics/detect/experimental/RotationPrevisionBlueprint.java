package de.jpx3.intave.check.combat.heuristics.detect.experimental;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.BlueprintLayout;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.EntityShade;
import de.jpx3.intave.shade.ClientMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;

import java.util.List;

import static de.jpx3.intave.check.combat.heuristics.detect.experimental.RotationPrevisionBlueprintMeta.RotationData;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public abstract class RotationPrevisionBlueprint<M extends RotationPrevisionBlueprintMeta>
  extends BlueprintLayout<Heuristics, RotationPrevisionBlueprintMeta, M> {
  private final int sampleSize;

  // Could use bit-shift operations for these options in constructor?
  public RotationPrevisionBlueprint(Heuristics parentCheck, Class<M> metaClass, int sampleSize) {
    super(parentCheck, metaClass);
    this.sampleSize = sampleSize;
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
    if (!user.meta().protocol().flyingPacketStream()) {
      return;
    }
    meta.lastAttack++;
    EntityShade target = user.meta().attack().lastAttackedEntity();
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
//      && meta.lastAttack <= 1
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

  public double average(List<Integer> values) {
    return values.stream()
      .mapToDouble(Number::doubleValue)
      .average()
      .orElse(0D);
  }

  private RotationData createRotationData(MovementMetadata movementData, EntityShade target) {
    float lastPlayerYaw = ClientMathHelper.wrapAngleTo180_float(movementData.lastRotationYaw);
    float yawDelta = MathHelper.distanceInDegrees(ClientMathHelper.wrapAngleTo180_float(movementData.rotationYaw), lastPlayerYaw);

    float pitchDelta = Math.abs(movementData.rotationPitch - movementData.lastRotationPitch);

    float expectedYaw = expectedYaw(target.position, movementData.lastPositionX, movementData.lastPositionZ);
    float expectedPitch = expectedPitch(target.position, movementData.lastPositionX, movementData.lastPositionY, movementData.lastPositionZ);

    float expectedYawDelta = MathHelper.distanceInDegrees(expectedYaw, lastPlayerYaw);
    float expectPitchDelta = Math.abs(expectedPitch - movementData.rotationPitch);
    return new RotationData(yawDelta, expectedYawDelta, pitchDelta, expectPitchDelta);
  }

  private float expectedYaw(EntityShade.EntityPositionContext entityPositions, double posX, double posZ) {
    final double diffX = entityPositions.posX - posX;
    final double diffZ = entityPositions.posZ - posZ;
    return (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
  }

  private static float expectedPitch(EntityShade.EntityPositionContext entityPositions, double posX, double posY, double posZ) {
    double diffY = entityPositions.posY + 1.62f - (posY + 1.62f);
    double diffX = entityPositions.posX - posX;
    double diffZ = entityPositions.posZ - posZ;
    double d3 = Math.sqrt(diffX * diffX + diffZ * diffZ);
    return (float) (-Math.atan2(diffY, d3) * 180.0 / Math.PI);
  }
}


package de.jpx3.intave.module.nayoro;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketEventSubscriber;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketEventDispatch implements PacketEventSubscriber {
  private final BiConsumer<User, Consumer<EventSink>> reverseSink;

  public PacketEventDispatch(BiConsumer<User, Consumer<EventSink>> sinkCallback) {
    this.reverseSink = sinkCallback;
  }

  @PacketSubscription(
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void onClick(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    ClickEvent clickEvent = ClickEvent.create();
    reverseSink.accept(user, clickEvent::accept);
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY
    }
  )
  public void onUse(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    EntityUseReader packetReader = PacketReaders.readerOf(packet);
    if (packetReader.useAction() == EnumWrappers.EntityUseAction.ATTACK) {
      int attackerId = player.getEntityId();
      int targetId = packetReader.entityId();
      AttackEvent attackEvent = AttackEvent.create(attackerId, targetId);
      reverseSink.accept(user, attackEvent::accept);
    }
    packetReader.release();
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementMetadata movement = user.meta().movement();
    double x = movement.positionX;
    double y = movement.positionY;
    double z = movement.positionZ;
    double lastX = movement.lastPositionX;
    double lastY = movement.lastPositionY;
    double lastZ = movement.lastPositionZ;
    float yaw = movement.rotationYaw;
    float pitch = movement.rotationPitch;
    float lastYaw = movement.lastRotationYaw;
    float lastPitch = movement.lastRotationPitch;
    PlayerMoveEvent movementEvent;
    if (movement.recordedMoves++ % 200 == 0) {
      movementEvent = PlayerMoveEvent.create(
        x, y, z,
        yaw, pitch
      );
    } else {
      movementEvent = PlayerMoveEvent.create(
        x, y, z, lastX, lastY, lastZ,
        yaw, pitch, lastYaw, lastPitch
      );
    }
    reverseSink.accept(user, movementEvent::accept);
  }
}

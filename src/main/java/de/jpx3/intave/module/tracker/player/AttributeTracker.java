package de.jpx3.intave.module.tracker.player;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.Property;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes.PropertyModifier;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.UPDATE_ATTRIBUTES;

public final class AttributeTracker extends Module {
  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      UPDATE_ATTRIBUTES
    }
  )
  public void sentAttributes(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    WrapperPlayServerUpdateAttributes packet = new WrapperPlayServerUpdateAttributes((PacketSendEvent) event);
    if (packet.getEntityId() == player.getEntityId()) {
      List<Property> attributes = packet.getProperties();
      packet.setProperties(attributes);
      event.markForReEncode(true);
      user.tickFeedback(() -> {
        attributes.forEach(attribute -> receivedAttribute(user, attribute));
      });
    }
  }

  private void receivedAttribute(User user, Property attribute) {
    AbilityMetadata abilities = user.meta().abilities();
    MovementMetadata movement = user.meta().movement();
    if (abilities.findAttribute(attribute.getKey()) != null) {
      List<PropertyModifier> intaveAttributes = abilities.modifiersOf(attribute);
      intaveAttributes.clear();
      List<PropertyModifier> serverAttributes = attribute.getModifiers();
      movement.hasSprintSpeed = serverAttributes.stream().anyMatch(MovementMetadata::isSprintingModifier);
      intaveAttributes.addAll(serverAttributes);
      abilities.modifyBaseValue(attribute.getKey(), attribute.getValue());
    }
  }
}

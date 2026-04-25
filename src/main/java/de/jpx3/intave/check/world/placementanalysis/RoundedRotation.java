package de.jpx3.intave.check.world.placementanalysis;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.PlacementAnalysis;
import de.jpx3.intave.connect.sibyl.SibylMessageTransmitter;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public class RoundedRotation extends MetaCheckPart<PlacementAnalysis, RoundedRotation.Meta> {
  private final IntavePlugin plugin = IntavePlugin.singletonInstance();
  private final static int MIN_ACTIVATION_DATA = 100;

  public RoundedRotation(PlacementAnalysis parentCheck) {
    super(parentCheck, Meta.class);
  }

  @PacketSubscription(
    packetsIn = {
      LOOK, POSITION_LOOK
    }
  )
  public void receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);

    Meta meta = metaOf(user);
    MovementMetadata movement = user.meta().movement();

    float rotationPitch = movement.rotationPitch;
    int firstZero = firstZeroInDecimal(rotationPitch);

    boolean recentlyPlaced = movement.pastBlockPlacement < 20;
    int[] zerosBuilding = meta.zerosBuilding;
    int[] zerosNotBuilding = meta.zerosNotBuilding;

    int[] destination = recentlyPlaced ? zerosBuilding : zerosNotBuilding;
    int index = recentlyPlaced ? meta.indexBuilding++ : meta.indexNotBuilding++;

    destination[index % destination.length] = firstZero;

    boolean activated = meta.indexBuilding > MIN_ACTIVATION_DATA
      && meta.indexNotBuilding > MIN_ACTIVATION_DATA;

    if (!activated) {
      return;
    }

    if (Math.abs(meta.lastCheckedBuildingIndex - meta.indexBuilding) > 20) {
      int avgBuilding = averageOf(zerosBuilding);
      int avgNotBuilding = averageOf(zerosNotBuilding);

      if (avgNotBuilding <= 3 && avgBuilding >= 6) {
//        String format = "[Intave] %s has sus 0 in rots [notBuilding: %s, building: %s]";
//        sendDebug(String.format(format, player.getName(), avgNotBuilding, avgBuilding));
      }

      meta.lastCheckedBuildingIndex = meta.indexBuilding;
    }
  }

  private void sendDebug(String message) {
    for (Player authenticatedPlayer : MessageChannelSubscriptions.sibylReceivers()) {
      if (plugin.sibyl().isAuthenticated(authenticatedPlayer)) {
        SibylMessageTransmitter.sendMessage(authenticatedPlayer, message);
      }
    }
  }

  private static int averageOf(int[] ints) {
    int count = 0;
    for (int i : ints) {
      count += i;
    }
    return count / ints.length;
  }

  private int firstZeroInDecimal(float value) {
    int index = String.valueOf((int) ((value % 1) * 100000 + 0.5)).indexOf('0');
    return index == -1 ? 8 : index;
  }

  public static class Meta extends CheckCustomMetadata {
    int indexNotBuilding;
    public int[] zerosNotBuilding = new int[60];
    public int indexBuilding;
    public int[] zerosBuilding = new int[10];
    public int lastCheckedBuildingIndex;
  }
}

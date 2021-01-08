package de.jpx3.intave.event.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.user.UserRepository;

import java.util.Collection;

public final class ForwardingPacketAdapter extends PacketAdapter {
  private final Collection<LocalPacketAdapter> targetList;

  public ForwardingPacketAdapter(
    IntavePlugin plugin,
    PacketType packetType,
    Collection<LocalPacketAdapter> targetList
  ) {
    super(plugin, ListenerPriority.LOWEST, packetType);
    this.targetList = targetList;
  }

  @Override
  public void onPacketSending(PacketEvent event) {
    for (LocalPacketAdapter localPacketAdapter : targetList) {
      localPacketAdapter.onPacketSending(event);
    }
  }

  @Override
  public void onPacketReceiving(PacketEvent event) {
    if(UserRepository.userOf(event.getPlayer()).shouldIgnoreNextPacket()) {
      UserRepository.userOf(event.getPlayer()).receiveNextPacket();
      return;
    }

    for (LocalPacketAdapter localPacketAdapter : targetList) {
      localPacketAdapter.onPacketReceiving(event);
    }
  }

  public void tryRemovePluginReference() {
    plugin = null;
  }
}

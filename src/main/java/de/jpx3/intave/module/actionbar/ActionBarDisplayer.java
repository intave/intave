package de.jpx3.intave.module.actionbar;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.comphenix.protocol.PacketType.Play.Server.CHAT;
import static com.comphenix.protocol.PacketType.Play.Server.SET_ACTION_BAR_TEXT;
import static de.jpx3.intave.module.linker.packet.PacketId.Server.CHAT_OUT;

public final class ActionBarDisplayer extends Module {
  private final ClickFeeder clickFeeder = new ClickFeeder();
  private final Lock lock = new ReentrantLock();

  @Override
  public void enable() {
    Modules.linker().bukkitEvents().registerEventsIn(clickFeeder);
    Modules.linker().packetEvents().linkSubscriptionsIn(clickFeeder);
  }


  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsOut = {
      CHAT_OUT
    }
//    engine = Engine.ASYNC_INTERNAL
  )
  public void clientClickUpdate(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    Byte read = packet.getBytes().readSafely(0);
    EnumWrappers.ChatType type = packet.getChatTypes().read(0);
    if (((read != null && read.intValue() == 2) || (type == EnumWrappers.ChatType.GAME_INFO)) && inSubscription(user)) {
      event.setCancelled(true);
    }
  }

  public void subscribe(User receiver, User target, DisplayType type) {
    if (!receiver.hasPlayer() || !target.hasPlayer()) {
      return;
    }
    try {
      lock.lock();
      receiver.setActionTarget(target.id());
      target.addActionReceiver(receiver.id(), type);
      startTaskFor(receiver);
    } finally {
      lock.unlock();
    }
  }

  public boolean inSubscription(User receiver) {
    return receiver.actionTarget() != null;
  }

  public void unsubscribe(User receiver) {
    try {
      lock.lock();
      UUID id = receiver.actionTarget();
      receiver.setActionTarget(null);
      User target = UserRepository.userOf(id);
      target.removeActionSubscription(receiver.id());
    } finally {
      lock.unlock();
    }
  }

  private void startTaskFor(User receiver) {
    if (!receiver.hasPlayer()) {
      return;
    }
    UUID target = receiver.actionTarget();
    int[] counter = {0};
    int[] taskId = new int[1];
    taskId[0] = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
      boolean cancelTask = counter[0]++ >= 20 * 60 * 15 || !receiver.hasPlayer() || !receiver.player().isOnline() || !inSubscription(receiver) || receiver.actionTarget() != target;
      User targetUser = UserRepository.userOf(target);
      cancelTask |= !targetUser.hasPlayer() || !targetUser.player().isOnline() || !targetUser.anyActionSubscriptions();
      if (cancelTask) {
//        System.out.println("CANCELLED " + counter[0] + " " + !receiver.hasPlayer() + " " + !receiver.player().isOnline() + " " + !inSubscription(receiver) + " " + (receiver.actionTarget() != target) + " " + !targetUser.hasPlayer() + " " + !targetUser.player().isOnline() + " " + !targetUser.anyActionSubscriptions());
        Bukkit.getScheduler().cancelTask(taskId[0]);
        TaskTracker.stopped(taskId[0]);
        unsubscribe(receiver);
        return;
      }
      String text = targetUser.actionDisplayOf(DisplayType.CLICKS);
      if (text != null) {
        sendActionBar(receiver.player(), text);
      }
    }, 1, 1);
    TaskTracker.begun(taskId[0]);
  }

  private static final boolean TYPE_AS_GAME_INFO = MinecraftVersions.VER1_12_0.atOrAbove();
  private static final boolean DEDICATED_ACTION_BAR_PACKET = MinecraftVersions.VER1_17_0.atOrAbove();

  private void sendActionBar(Player player, String message) {
    PacketContainer packet = new PacketContainer(DEDICATED_ACTION_BAR_PACKET ? SET_ACTION_BAR_TEXT : CHAT);
    packet.getChatComponents().write(0, WrappedChatComponent.fromText(message));

    if (!DEDICATED_ACTION_BAR_PACKET) {
      if (TYPE_AS_GAME_INFO) {
        packet.getChatTypes().write(0, EnumWrappers.ChatType.GAME_INFO);
      } else {
        packet.getBytes().write(0, (byte) 2);
      }
    }

    PacketSender.sendServerPacketWithoutEvent(player, packet);
//    Synchronizer.synchronize(() -> {
//      player.sendMessage(message);
//    });
  }

  @Override
  public void disable() {

  }
}

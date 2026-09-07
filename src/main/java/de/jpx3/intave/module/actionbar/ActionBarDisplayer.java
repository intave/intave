/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.actionbar;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.view.ChatOutView;
import de.jpx3.intave.packet.view.PacketEventsChatOutView;
import de.jpx3.intave.packet.view.ProtocolLibChatOutView;
import de.jpx3.intave.player.ActionBar;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
  public void clientClickUpdate(PacketEvent event) {
    handleChatOut(new ProtocolLibChatOutView(event));
  }

  /**
   * PacketEvents entry point for the same packet. Which chat slot the packet targets is the only
   * thing read, so the engine neutral {@link ChatOutView} carries it.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsOut = {
      CHAT_OUT
    }
  )
  public void clientClickUpdate(PacketSendEvent event) {
    PacketEventsChatOutView view = PacketEventsChatOutView.of(event);
    if (view == null) {
      return;
    }
    handleChatOut(view);
  }

  /**
   * Engine independent handling: foreign action bar text is suppressed while the receiver watches
   * another player, so the display Intave pushes is not overwritten.
   */
  private void handleChatOut(ChatOutView view) {
    Player player = view.player();
    User user = UserRepository.userOf(player);
    if (view.isActionBar() && inSubscription(user)) {
      view.setCancelled(true);
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
    Task[] task = new Task[1];
    task[0] = Tasks.periodicNamed("ActionBarDisplayer.update", () -> {
      boolean cancelTask = counter[0]++ >= 20 * 60 * 15 || !receiver.hasPlayer() || !receiver.player().isOnline() || !inSubscription(receiver) || receiver.actionTarget() != target;
      User targetUser = UserRepository.userOf(target);
      cancelTask |= !targetUser.hasPlayer() || !targetUser.player().isOnline() || !targetUser.anyActionSubscriptions();
      if (cancelTask) {
//        System.out.println("CANCELLED " + counter[0] + " " + !receiver.hasPlayer() + " " + !receiver.player().isOnline() + " " + !inSubscription(receiver) + " " + (receiver.actionTarget() != target) + " " + !targetUser.hasPlayer() + " " + !targetUser.player().isOnline() + " " + !targetUser.anyActionSubscriptions());
        task[0].cancel();
        unsubscribe(receiver);
        return;
      }
      String text = targetUser.actionDisplayOf(DisplayType.CLICKS);
      if (text != null) {
        ActionBar.sendActionBar(receiver.player(), text);
      }
    }, 1, 1).startUserSync(receiver);
  }

  @Override
  public void disable() {

  }
}

package de.jpx3.intave.check.movement.timer;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.movement.Timer;
import de.jpx3.intave.executor.IntaveThreadFactory;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.module.feedback.FeedbackOptions.SELF_SYNCHRONIZATION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

@Deprecated
public final class BalanceButActuallyGood extends MetaCheckPart<Timer, BalanceButActuallyGood.MovementFrequencyData> {
  private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(IntaveThreadFactory.ofLowestPriority());

  public BalanceButActuallyGood(Timer parentCheck) {
    super(parentCheck, MovementFrequencyData.class);

    setupScheduler();
  }

  private void setupScheduler() {
    Runnable command = () -> Bukkit.getOnlinePlayers().forEach(this::transaction);
    executorService.scheduleAtFixedRate(
      command,
      20,
      50,
      TimeUnit.MILLISECONDS
    );
  }

  private void transaction(Player player) {
    User user = userOf(player);
    user.tickFeedback(() -> checkPackets(user), SELF_SYNCHRONIZATION);
  }

  private void checkPackets(User user) {
    Player player = user.player();
    MovementFrequencyData frequencyData = metaOf(user);

    frequencyData.balance++;

    if (user.justJoined()) {
      frequencyData.balance = 1;
    }

    ChatColor chatColor = frequencyData.balance < 0 ? ChatColor.RED : ChatColor.GRAY;
    // player.sendMessage(chatColor + MathHelper.formatDouble(frequencyData.balance, 2));

    frequencyData.lastBalance = frequencyData.balance;

    if (frequencyData.balance < 0) {
      Violation violation = Violation.builderFor(Timer.class).forPlayer(player)
        .withMessage("moved too frequently")
        .withVL(0.5)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        MovementMetadata movementData = user.meta().movement();
        movementData.invalidMovement = true;
        Vector setback = new Vector(movementData.baseMotionX, movementData.baseMotionY, movementData.baseMotionZ);
        Modules.mitigate().movement().emulationSetBack(player, setback, 12, false);
      }

      reset(user);
    }
  }

  @PacketSubscription(
    packetsIn = {
      POSITION_LOOK, POSITION, FLYING, LOOK
    }
  )
  public void clientTickUpdate(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MovementFrequencyData frequencyData = metaOf(user);

    frequencyData.balance--;
  }

  @PacketSubscription(
    packetsOut = {
      PacketId.Server.POSITION
    }
  )
  public void catchTeleport(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    MovementFrequencyData frequencyData = metaOf(user);

    frequencyData.balance++;
  }

  public void reset(User user) {
    metaOf(user).balance = 0;
  }

  public static final class MovementFrequencyData extends CheckCustomMetadata {
    public boolean lock;
    public int balance = 1, lastBalance;
    public double vl;
  }
}
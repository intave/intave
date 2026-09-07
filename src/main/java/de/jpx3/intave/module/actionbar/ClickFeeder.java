package de.jpx3.intave.module.actionbar;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.check.combat.clickpatterns.Kurtosis;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.view.PacketEventsAttackView;
import de.jpx3.intave.packet.view.PacketEventsMovementView;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

import static com.comphenix.protocol.wrappers.EnumWrappers.PlayerDigType.DROP_ITEM;
import static de.jpx3.intave.math.MathHelper.formatDouble;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;
import static java.lang.Math.pow;

public final class ClickFeeder implements EventProcessor {
  private final UserLocal<ClickBufferData> bufferData = UserLocal.withInitial(ClickBufferData::new);

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY, ARM_ANIMATION, BLOCK_DIG, USE_ITEM
    }
  )
  public void clientClickUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);
    PacketContainer packet = event.getPacket();
    PacketType type = packet.getType();
    if (type == PacketType.Play.Client.USE_ENTITY) {
      EntityUseReader reader = PacketReaders.readerOf(packet);
      EnumWrappers.EntityUseAction entityUseAction = reader.useAction();
      if (entityUseAction == EnumWrappers.EntityUseAction.ATTACK) {
        countAttack(user);
      }
      reader.release();
    } else if (type == PacketType.Play.Client.ARM_ANIMATION) {
      countSwing(user);
    } else if (type == PacketType.Play.Client.BLOCK_DIG) {
      countDig(user, packet.getPlayerDigTypes().read(0) == DROP_ITEM);
    } else {
      countPlace(user);
    }
  }

  /**
   * PacketEvents entry point for the same four packets. PacketEvents folds the separate attack
   * packet of the newest protocols back into the interact packet, so the attack is told apart by
   * the action just like the ProtocolLib reader does below 26.1.1.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      USE_ENTITY, ARM_ANIMATION, BLOCK_DIG, USE_ITEM
    }
  )
  public void clientClickUpdate(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    User user = UserRepository.userOf(player);
    PacketTypeCommon type = event.getPacketType();
    if (type == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.INTERACT_ENTITY) {
      PacketEventsAttackView view = PacketEventsAttackView.of(event);
      if (view == null) {
        return;
      }
      if (view.isAttackPacket()) {
        countAttack(user);
      }
      view.release();
    } else if (type == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.ANIMATION) {
      countSwing(user);
    } else if (type == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_DIGGING) {
      DiggingAction digType = new WrapperPlayClientPlayerDigging(event).getAction();
      countDig(user, digType == DiggingAction.DROP_ITEM);
    } else {
      countPlace(user);
    }
  }

  /** Engine independent: an attack on an entity happened. */
  private void countAttack(User user) {
    this.bufferData.get(user).recordAttack(System.currentTimeMillis());
  }

  /** Engine independent: a bare arm swing happened. */
  private void countSwing(User user) {
    ClickBufferData bufferData = this.bufferData.get(user);
    bufferData.recordClick(System.currentTimeMillis());
    if (System.currentTimeMillis() - bufferData.lastMove > 200) {
      bufferData.desynchronizedClick = true;
    }
  }

  /**
   * Engine independent dig handling. A drop with an empty hand is the display's tab cycle gesture
   * and never counts as a place, exactly as the ProtocolLib path did.
   */
  private void countDig(User user, boolean dropItem) {
    if (dropItem && user.meta().inventory().heldItemType() == Material.AIR) {
      UUID actionTarget = user.actionTarget();
      if (actionTarget != null) {
        User actionTargetUser = UserRepository.userOf(actionTarget);
        if (actionTargetUser.hasPlayer()) {
          ClickBufferData otherBufferData = this.bufferData.get(actionTargetUser);
          otherBufferData.tab++;
          otherBufferData.tab %= otherBufferData.totalTabs;
          otherBufferData.frontVisible = 0;
          otherBufferData.changeDisplayVisible = 0;
          Arrays.fill(otherBufferData.tabVisibility, 0);
        }
      }
      return;
    }
    countPlace(user);
  }

  /** Engine independent: a block place or item use happened. */
  private void countPlace(User user) {
    ClickBufferData bufferData = this.bufferData.get(user);
    bufferData.breakingBlock = user.meta().attack().inBreakProcess;
    bufferData.recordPlace(System.currentTimeMillis());
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END
    }
  )
  public void clientTickUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);

    MoveKind kind = kindOf(event.getPacketType());
    ProtocolMetadata protocol = user.meta().protocol();
    boolean sendsClientTickEnd = protocol.sendsClientTickEnd();

    if (sendsClientTickEnd && kind != MoveKind.CLIENT_TICK_END) {
      return;
    }

    ClickBufferData bufferData = this.bufferData.get(user);
    boolean positionReminderPacket = false;
    if (user.protocolVersion() > ProtocolMetadata.VER_1_8
      && !sendsClientTickEnd
      && carriesPosition(kind)
    ) {
      positionReminderPacket = recordPositionReminder(
        bufferData, protocol,
        event.getPacket().getDoubles().read(0),
        event.getPacket().getDoubles().read(1),
        event.getPacket().getDoubles().read(2)
      );
    }
    handleTickUpdate(user, kind, bufferData, sendsClientTickEnd, positionReminderPacket);
  }

  /**
   * PacketEvents entry point for the same five packets. The tick update only needs to know which
   * movement packet arrived and, for the two that carry coordinates, where the player claims to be;
   * both come off {@link PacketEventsMovementView}, so no engine type reaches the shared body.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END
    }
  )
  public void clientTickUpdate(PacketReceiveEvent event, Player player) {
    if (player == null) {
      return;
    }
    User user = UserRepository.userOf(player);

    MoveKind kind = kindOf(event.getPacketType());
    ProtocolMetadata protocol = user.meta().protocol();
    boolean sendsClientTickEnd = protocol.sendsClientTickEnd();

    if (sendsClientTickEnd && kind != MoveKind.CLIENT_TICK_END) {
      return;
    }

    ClickBufferData bufferData = this.bufferData.get(user);
    boolean positionReminderPacket = false;
    if (user.protocolVersion() > ProtocolMetadata.VER_1_8
      && !sendsClientTickEnd
      && carriesPosition(kind)
    ) {
      PacketEventsMovementView view = PacketEventsMovementView.of(event);
      if (view != null) {
        positionReminderPacket = recordPositionReminder(
          bufferData, protocol, view.positionX(), view.positionY(), view.positionZ()
        );
      }
    }
    handleTickUpdate(user, kind, bufferData, sendsClientTickEnd, positionReminderPacket);
  }

  /**
   * Engine independent tick update. {@link MoveKind} stands in for the engine's packet type
   * constant, which is the only part of the packet this body ever looked at.
   */
  private void handleTickUpdate(
    User user,
    MoveKind kind,
    ClickBufferData bufferData,
    boolean sendsClientTickEnd,
    boolean positionReminderPacket
  ) {
    TickSample sample = resolveTickSample(bufferData.clicks, bufferData.attacks, bufferData.places);
    long now = System.currentTimeMillis();

    if (user.anyActionSubscriptions()) {
      boolean reconstructTicks = false;
      int ticksToAdvance = 1;
      if (user.protocolVersion() > ProtocolMetadata.VER_1_8
        && !sendsClientTickEnd
        && bufferData.lastMovePacketType != null
      ) {
        SimulationEnvironment movement = user.meta().movement();
        reconstructTicks = positionReminderPacket
          || movement.receivedFlyingPacketIn(0)
          || bufferData.lastMovePacketType == MoveKind.FLYING
          || bufferData.lastMovePacketType == MoveKind.LOOK;
        if (reconstructTicks) {
          ticksToAdvance = positionReminderPacket
            ? 20
            : Math.max(1, (int) ((now - bufferData.lastTickTimeStamp) / 50f));
        }
      }
      if (reconstructTicks) {
        appendBufferedTicks(bufferData, now, ticksToAdvance);
      } else {
        bufferData.append(sample.action, sample.intensity);
      }
      String text;
      if (bufferData.anyVisible < 15) {
        text = ChatColor.GRAY + "Intave Recordbar Display";
      } else if (bufferData.anyVisible < 30) {
        text = ChatColor.GRAY + "Cycle with Q on empty hand";
      } else if (bufferData.frontVisible < 10 && bufferData.changeDisplayVisible < 10) {
        String[] tabNames = bufferData.tabNames;
        StringBuilder textBuilder = new StringBuilder();
        for (int i = 0; i < tabNames.length; i++) {
          String tabName = tabNames[i];
          if (i != 0) {
            textBuilder.append(" | ");
          }
          if (i == bufferData.tab) {
            textBuilder.append(ChatColor.GRAY).append(ChatColor.UNDERLINE).append(tabName).append(ChatColor.GRAY);
          } else {
            textBuilder.append(ChatColor.GRAY).append(tabName).append(ChatColor.GRAY);
          }
        }
        text = textBuilder.toString();
        bufferData.changeDisplayVisible++;
        bufferData.frontVisible = 0;
        Arrays.fill(bufferData.tabVisibility, 0);
      } else if ((bufferData.tabVisibility[1] > 0 || bufferData.frontVisible == 0) && bufferData.tab == 1 && bufferData.tabVisibility[1] < 20) {
        text = ChatColor.GRAY + "C = Clicks, A = Attacks, P = Places, " + ChatColor.GREEN + "Once per tick" + ChatColor.GRAY + ", " + ChatColor.YELLOW + "Twice per tick" + ChatColor.GRAY + ", " + ChatColor.RED + "Three times per tick";
        bufferData.frontVisible = 1;
      } else if ((bufferData.tabVisibility[2] > 0 || bufferData.frontVisible == 0) && bufferData.tab == 2 && bufferData.tabVisibility[2] < 20) {
        text = ChatColor.GRAY + "History with " + ChatColor.RED + "6(" + ChatColor.GRAY + "streak" + ChatColor.RED + ")" + ChatColor.GRAY + " display";
        bufferData.frontVisible = 1;
      } else {
        text = bufferData.buildActionBar();
      }
      user.pushActionDisplayToSubscribers(DisplayType.CLICKS, text);
      bufferData.anyVisible++;
      bufferData.frontVisible++;
      if (bufferData.anyVisible >= 30) {
        bufferData.tabVisibility[bufferData.tab]++;
        for (int i = 0; i < bufferData.tabVisibility.length; i++) {
          if (i == bufferData.tab) {
            continue;
          }
          bufferData.tabVisibility[i] = 0;
        }
      }
    } else {
      bufferData.anyVisible = 0;
    }
    bufferData.attacks = 0;
    bufferData.clicks = 0;
    bufferData.places = 0;
    bufferData.attackTimestamps.clear();
    bufferData.clickTimestamps.clear();
    bufferData.placeTimestamps.clear();
    bufferData.desynchronizedClick = false;
    bufferData.lastMove = now;
    bufferData.lastMovePacketType = kind;
    bufferData.lastTickTimeStamp = now;
  }

  /** @return true for the two movement packets that carry coordinates. */
  private static boolean carriesPosition(MoveKind kind) {
    return kind == MoveKind.POSITION || kind == MoveKind.POSITION_LOOK;
  }

  /**
   * Engine independent half of the former {@code isPositionReminderPacket}: records the reported
   * position and reports whether it sits close enough to the previous one to be the client's idle
   * position reminder rather than a real move. Only called for {@link #carriesPosition} kinds, so
   * the packet type guard that used to open the method now lives at the call sites.
   */
  private boolean recordPositionReminder(
    ClickBufferData bufferData, ProtocolMetadata protocol,
    double positionX, double positionY, double positionZ
  ) {
    double offsetX = positionX - bufferData.lastReportedPositionX;
    double offsetY = positionY - bufferData.lastReportedPositionY;
    double offsetZ = positionZ - bufferData.lastReportedPositionZ;
    double distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
    double movementThresholdSquared = protocol.protocolVersion() >= ProtocolMetadata.VER_1_18_2
      ? 0.0002 * 0.0002
      : 9.0E-4;
    boolean reminder = bufferData.hasReportedPosition && distanceSquared <= movementThresholdSquared;
    bufferData.hasReportedPosition = true;
    bufferData.lastReportedPositionX = positionX;
    bufferData.lastReportedPositionY = positionY;
    bufferData.lastReportedPositionZ = positionZ;
    return reminder;
  }

  /** ProtocolLib packet type to engine neutral kind. */
  private static MoveKind kindOf(PacketType packetType) {
    if (PacketTypes.isClientEndTick(packetType)) {
      return MoveKind.CLIENT_TICK_END;
    }
    if (packetType == PacketType.Play.Client.POSITION_LOOK) {
      return MoveKind.POSITION_LOOK;
    }
    if (packetType == PacketType.Play.Client.POSITION) {
      return MoveKind.POSITION;
    }
    if (packetType == PacketType.Play.Client.LOOK) {
      return MoveKind.LOOK;
    }
    return MoveKind.FLYING;
  }

  /**
   * PacketEvents packet type to engine neutral kind. The subscription only ever delivers the five
   * kinds below; the tick end constant is matched by exclusion because it does not exist on every
   * PacketEvents release Intave has to run against.
   */
  private static MoveKind kindOf(PacketTypeCommon packetType) {
    if (packetType == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
      return MoveKind.POSITION_LOOK;
    }
    if (packetType == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_POSITION) {
      return MoveKind.POSITION;
    }
    if (packetType == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_ROTATION) {
      return MoveKind.LOOK;
    }
    if (packetType == com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_FLYING) {
      return MoveKind.FLYING;
    }
    return MoveKind.CLIENT_TICK_END;
  }

  /** The movement packets this feeder tells apart, independent of the packet engine. */
  private enum MoveKind {
    FLYING,
    LOOK,
    POSITION,
    POSITION_LOOK,
    CLIENT_TICK_END
  }

  static void appendBufferedTicks(ClickBufferData bufferData, long now, int ticksToAdvance) {
    for (int age = ticksToAdvance - 1; age >= 0; age--) {
      int clicks = countAtAge(bufferData.clickTimestamps, now, age);
      int attacks = countAtAge(bufferData.attackTimestamps, now, age);
      int places = countAtAge(bufferData.placeTimestamps, now, age);
      TickSample sample = resolveTickSample(clicks, attacks, places);
      bufferData.append(sample.action, sample.intensity);
    }
  }

  private static int countAtAge(List<Long> timestamps, long now, int age) {
    int count = 0;
    for (long timestamp : timestamps) {
      if ((int) ((now - timestamp) / 50f) == age) {
        count++;
      }
    }
    return count;
  }

  private static TickSample resolveTickSample(int clicks, int attacks, int places) {
    if (attacks > 0) {
      return new TickSample(TickAction.ATTACK, attacks);
    } else if (places > 0) {
      return new TickSample(TickAction.PLACE, places);
    } else if (clicks > 0) {
      return new TickSample(TickAction.CLICK, clicks);
    }
    return new TickSample(TickAction.NOTHING, 0);
  }

  private static final class TickSample {
    private final TickAction action;
    private final int intensity;

    private TickSample(TickAction action, int intensity) {
      this.action = action;
      this.intensity = intensity;
    }
  }

  public static class ClickBufferData {
    private final User user;
    private final List<TickAction> tickActions = new LinkedList<>();
    private final List<Integer> tickIntensity = new LinkedList<>();
    private final List<Boolean> unreliableTicks = new LinkedList<>();
    private final List<Integer> streakLength = new LinkedList<>();
    private final List<Long> clickTimestamps = new ArrayList<>();
    private final List<Long> attackTimestamps = new ArrayList<>();
    private final List<Long> placeTimestamps = new ArrayList<>();
    private int clicks, attacks, places;
    private boolean breakingBlock;
    private boolean desynchronizedClick;
    private int anyVisible = 0;
    private int frontVisible = 0;
    private int changeDisplayVisible = 0;
    private final int totalTabs = 4;
    private final int[] tabVisibility = new int[totalTabs];
    private final String[] tabNames = {"Basic", "History", "Streak", "Stats"};
    private int tab = 0;
    private long lastMove;
    private MoveKind lastMovePacketType;
    private long lastTickTimeStamp = System.currentTimeMillis();
    private boolean hasReportedPosition;
    private double lastReportedPositionX;
    private double lastReportedPositionY;
    private double lastReportedPositionZ;

    private int currentClickStreak;

    {
      for (int i = 0; i < 40; i++) {
        tickActions.add(TickAction.NOTHING);
        tickIntensity.add(0);
        unreliableTicks.add(false);
        streakLength.add(0);
      }
    }

    public ClickBufferData(User user) {
      this.user = user;
    }

    public synchronized void append(TickAction action, int intensity) {
      boolean unreliable = breakingBlock || desynchronizedClick;
      if (action == TickAction.NOTHING) {
        unreliable = false;
      }
      Boolean inBlockBreak = this.unreliableTicks.remove(0);
      this.unreliableTicks.add(unreliable);
      TickAction removed = tickActions.remove(0);

      if (removed == TickAction.NOTHING || inBlockBreak) {
      } else {
      }
      this.streakLength.remove(0);
      if (action == TickAction.NOTHING) {
        this.streakLength.add(currentClickStreak > 4 ? currentClickStreak : 0);
        currentClickStreak = 0;
      } else {
        this.streakLength.add(0);
        currentClickStreak++;
      }
      tickActions.add(action);
      tickIntensity.remove(0);
      tickIntensity.add(intensity);
    }

    void recordClick(long timestamp) {
      clicks++;
      clickTimestamps.add(timestamp);
    }

    void recordAttack(long timestamp) {
      attacks++;
      attackTimestamps.add(timestamp);
    }

    void recordPlace(long timestamp) {
      places++;
      placeTimestamps.add(timestamp);
    }

    TickAction actionAtAge(int age) {
      return tickActions.get(tickActions.size() - 1 - age);
    }

    int intensityAtAge(int age) {
      return tickIntensity.get(tickIntensity.size() - 1 - age);
    }

    public String buildActionBar() {
      int attackTicks = 0, clickTicks = 0;
      int whileBreaking = 0;

      for (int i = tickActions.size() - 1; i >= tickIntensity.size() / 2; i--) {
        TickAction tickAction = tickActions.get(i);
        Integer intensity = tickIntensity.get(i);
        if (tickAction == TickAction.ATTACK) {
          attackTicks += intensity;
          clickTicks += intensity;
        }
        if (tickAction == TickAction.CLICK) {
          clickTicks += intensity;
        }
        if (unreliableTicks.get(i)) {
          whileBreaking += intensity;
        }
      }

      StringBuilder builder = new StringBuilder();
      builder.append("&c");
      builder.append(user.player().getName());
      builder.append(" &7| ");

      int tabVisibility = this.tabVisibility[tab];

      if (attackTicks == 0 && clickTicks == 0 && (frontVisible / 20) % 2 == 0 && frontVisible <= 60) {
        builder.append("A/C");
      } else {
        boolean breakBlock = whileBreaking > 5;
        if (breakingBlock) {
//          builder.append(ChatColor.STRIKETHROUGH);
        }
        builder.append(attackTicks);
        if (breakingBlock) {
          builder.append(ChatColor.GRAY);
        }
        builder.append("/");
        if (breakingBlock) {
          builder.append(ChatColor.STRIKETHROUGH);
        }
        builder.append(clickTicks);
        if (breakingBlock) {
          builder.append(ChatColor.GRAY);
        }
      }

      if (tab == 1) {
        builder.append(" | ");
        for (int i = tickIntensity.size() - 1; i >= 0; i--) {
          TickAction tickAction = tickActions.get(i);
          int intensity = tickIntensity.get(i);
          if (intensity == 0) {
            builder.append("&7");
          } else if (intensity == 1) {
            builder.append("&a");
          } else if (intensity == 2) {
            builder.append("&e");
          } else if (intensity >= 3) {
            builder.append("&c");
          }
          if (unreliableTicks.get(i)) {
            builder.append(ChatColor.STRIKETHROUGH);
          }
          builder.append(tickAction.repChar());
        }
      } else if (tab == 2) {
        builder.append(" ").append(ChatColor.STRIKETHROUGH).append("|").append(ChatColor.GRAY).append(" ");

        int currentStreak = 0;
        int suspiciousStreak = 3;
        int weirdStreak = 4;
        int corruptStreak = 6;

        StringBuilder clickBuilder = new StringBuilder();
        boolean inClickStreak = false;
        ChatColor streakIndicator = ChatColor.GRAY;

        int[] suspiciousPauses = new int[tickIntensity.size()];

        int repeatedPausesExpectedCount = -1;
        int repeatedPausesCount = 0;
        int vl = 0;

        List<Integer> positionsInStreak = new LinkedList<>();

        for (int i = tickActions.size() - 1; i >= 0; i--) {
          TickAction tickAction = tickActions.get(i);

          if (tickAction == TickAction.NOTHING) {
            repeatedPausesCount++;
          } else if (repeatedPausesCount > 0) {
            if (repeatedPausesExpectedCount == -1) {
              repeatedPausesExpectedCount = repeatedPausesCount;
            }
            if (repeatedPausesCount == repeatedPausesExpectedCount) {
              positionsInStreak.add(i);
              vl++;
            } else {
              if (vl > 2) {
                for (Integer integer : positionsInStreak) {
                  suspiciousPauses[integer] = vl;
                }
              }
              repeatedPausesExpectedCount = repeatedPausesCount;
              positionsInStreak.clear();
              vl = 0;
            }
            repeatedPausesCount = 0;
          }
        }

        if (positionsInStreak.size() > 0) {
          for (Integer integer : positionsInStreak) {
            suspiciousPauses[integer] = vl;
          }
        }

        for (int i = tickIntensity.size() - 1; i >= 0; i--) {
          TickAction tickAction = tickActions.get(i);

          // just for the beginning streak
          if (tickAction == TickAction.NOTHING) {
            if (inClickStreak) {
              inClickStreak = false;
              clickBuilder.append(streakIndicator).append(") ");
              continue;
            }

            if (currentStreak > 0) {
              String text = "";
              if (currentStreak > corruptStreak) {
                text = ChatColor.RED + ")";
              } else if (currentStreak > weirdStreak) {
                text = ChatColor.YELLOW + ")";
              } else if (currentStreak > suspiciousStreak) {
                text = ")";
              }
              if (!"".equals(text)) {
                clickBuilder.append(text);
              }
            } else if (streakLength.get(i) > 0) {
              int pastClickStreak = streakLength.get(i);
              String text = "";
              ChatColor streakColor = ChatColor.GRAY;
              if (pastClickStreak > corruptStreak) {
                text = " " + ChatColor.RED + pastClickStreak + "(";
                streakColor = ChatColor.RED;
              } else if (pastClickStreak > weirdStreak) {
                text = " " + ChatColor.YELLOW + pastClickStreak + "(";
                streakColor = ChatColor.YELLOW;
              } else if (pastClickStreak > suspiciousStreak) {
                text = "(";
              }
              if (!"".equals(text)) {
                clickBuilder.append(text);
                inClickStreak = true;
                streakIndicator = streakColor;
                continue;
              }
            } else {
//              if (suspiciousPauses[i] > 2) {
//                clickBuilder.append(ChatColor.RED).append("-").append(ChatColor.GRAY);
//                builder.append(clickBuilder);
//                continue;
//              }
//              clickBuilder.append(ChatColor.GRAY).append(suspiciousPauses[i]).append(ChatColor.GRAY);
//              continue;
            }
            currentStreak = -100000;
          } else {
            currentStreak++;
          }

          int intensity = tickIntensity.get(i);
          if (intensity == 0) {
            clickBuilder.append("&7");
          } else if (intensity == 1) {
            clickBuilder.append("&a");
          } else if (intensity == 2) {
            clickBuilder.append("&e");
          } else if (intensity >= 3) {
            clickBuilder.append("&c");
          }
          if (unreliableTicks.get(i)) {
            clickBuilder.append(ChatColor.STRIKETHROUGH);
          }
          clickBuilder.append(tickAction.repChar());
        }

        builder.append(clickBuilder);

      } else if (tab == 3) {
        builder.append(" | ");
        Kurtosis.KurtosisMeta kurtosis = (Kurtosis.KurtosisMeta) user.checkMetadata(Kurtosis.KurtosisMeta.class);
        boolean displayMeaning = tabVisibility / 20 % 2 == 0 && tabVisibility <= 60;

        if (displayMeaning) {
          builder.append("KURTOS");
        } else {
          builder.append(formatDouble(kurtosisOf(kurtosis.attacks), 2));
        }

        builder.append(" ");
        if (displayMeaning) {
          builder.append("SKEWNS");
        } else {
          builder.append(formatDouble(skewnessOf(kurtosis.attacks), 2));
        }

        builder.append(" ");
        if (displayMeaning) {
          builder.append("STDDEV");
        } else {
          builder.append(formatDouble(standardDeviationOf(kurtosis.attacks), 2));
        }
      }

      return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }
  }

  private static double kurtosisOf(Collection<? extends Number> input) {
    double sum = 0;
    int amount = 0;
    for (Number number : input) {
      sum += number.doubleValue();
      ++amount;
    }
    if (amount < 3.0) {
      return 0.0;
    }
    double d2 = amount * (amount + 1.0) / ((amount - 1.0) * (amount - 2.0) * (amount - 3.0));
    double d3 = 3.0 * pow(amount - 1.0, 2.0) / ((amount - 2.0) * (amount - 3.0));
    double average = sum / amount;
    double s2 = 0.0;
    double s4 = 0.0;
    for (Number number : input) {
      s2 += pow(average - number.doubleValue(), 2);
      s4 += pow(average - number.doubleValue(), 4);
    }
    return d2 * (s4 / pow(s2 / sum, 2)) - d3;
  }

  private static double skewnessOf(Collection<? extends Number> sd) {
    int amount = sd.size();
    if (amount == 0) {
      return 0;
    }
    double total = 0;
    List<Double> numbersAsDoubles = new ArrayList<>();
    for (Number number : sd) {
      double numberAsDouble = number.doubleValue();
      total += numberAsDouble;
      numbersAsDoubles.add(numberAsDouble);
    }
    numbersAsDoubles.sort(Double::compareTo);
    double mean = total / amount;
    double median = numbersAsDoubles.get((amount % 2 != 0 ? amount : amount - 1) / 2);
    return 3 * (mean - median) / standardDeviationOf(numbersAsDoubles);
  }

  private static double standardDeviationOf(Collection<? extends Number> sd) {
    double sum = 0, newSum = 0;
    for (Number v : sd) {
      sum = sum + v.doubleValue();
    }
    double mean = sum / sd.size();
    for (Number v : sd) {
      newSum = newSum + (v.doubleValue() - mean) * (v.doubleValue() - mean);
    }
    return Math.sqrt(newSum / sd.size());
  }

  public enum TickAction {
    NOTHING(' '),
    CLICK('C'),
    ATTACK('A'),
    PLACE('P'),
    ;
    private final char representation;

    TickAction(char representation) {
      this.representation = representation;
    }

    public char repChar() {
      return representation;
    }
  }
}

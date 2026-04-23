package de.jpx3.intave.module.actionbar;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.EventProcessor;
import de.jpx3.intave.check.combat.clickpatterns.Kurtosis;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
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
    ClickBufferData bufferData = this.bufferData.get(user);
    PacketContainer packet = event.getPacket();
    PacketType type = packet.getType();
    if (type == PacketType.Play.Client.USE_ENTITY) {
      EntityUseReader reader = PacketReaders.readerOf(packet);
      EnumWrappers.EntityUseAction entityUseAction = reader.useAction();
      if (entityUseAction == EnumWrappers.EntityUseAction.ATTACK) {
        bufferData.attacks++;
      }
      reader.release();
    } else if (type == PacketType.Play.Client.ARM_ANIMATION) {
      bufferData.clicks++;
      if (System.currentTimeMillis() - bufferData.lastMove > 200) {
        bufferData.desynchronizedClick = true;
      }
    } else if (type == PacketType.Play.Client.BLOCK_DIG) {
      if (packet.getPlayerDigTypes().read(0) == DROP_ITEM && user.meta().inventory().heldItemType() == Material.AIR) {
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
      } else {
        bufferData.breakingBlock = user.meta().attack().inBreakProcess;
        bufferData.places++;
      }
    } else {
      bufferData.breakingBlock = user.meta().attack().inBreakProcess;
      bufferData.places++;
    }
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

    PacketType packetType = event.getPacketType();
    boolean sendsClientTickEnd = user.meta().protocol().sendsClientTickEnd();

    if (sendsClientTickEnd && packetType != PacketType.Play.Client.CLIENT_TICK_END) {
      return;
    }

    ClickBufferData bufferData = this.bufferData.get(user);
    TickAction action = TickAction.NOTHING;
    int intensity = 0;

    if (bufferData.clicks > 0) {
      action = TickAction.CLICK;
      intensity = bufferData.clicks;
    }
    if (bufferData.attacks > 0) {
      action = TickAction.ATTACK;
      intensity = bufferData.attacks;
    } else if (bufferData.places > 0) {
      action = TickAction.PLACE;
      intensity = bufferData.places;
    }
    if (user.anyActionSubscriptions()) {
      bufferData.append(action, intensity);
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
    bufferData.desynchronizedClick = false;
    bufferData.lastMove = System.currentTimeMillis();
  }

  public static class ClickBufferData {
    private final User user;
    private final List<TickAction> tickActions = new LinkedList<>();
    private final List<Integer> tickIntensity = new LinkedList<>();
    private final List<Boolean> unreliableTicks = new LinkedList<>();
    private final List<Integer> streakLength = new LinkedList<>();
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

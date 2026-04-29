package de.jpx3.intave.check.combat.heuristics.unused;

import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class ChannelActivityHeuristic extends MetaCheckPart<Heuristics, ChannelActivityHeuristic.ChannelActivityMeta> {
  public ChannelActivityHeuristic(Heuristics parentCheck) {
    super(parentCheck, ChannelActivityMeta.class);
  }

  public static final double AVERAGE = 50;

  @PacketSubscription(
    packetsIn = {
      POSITION_LOOK, POSITION, FLYING, LOOK
    }
  )
  public void clientTickUpdate(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);

    ChannelActivityMeta heuristic = metaOf(user);

    long now = System.currentTimeMillis();

    long lastFlying = heuristic.lastFlying;
    heuristic.lastFlying = now;

    if (lastFlying == -1) {
      return;
    }

    long diff = now - lastFlying;

    if (heuristic.differences.size() > 50) {
      double max = AVERAGE * 2.5;

      //player.sendMessage("diff:" + diff + ";" + MathHelper.formatDouble(max, 2));

      // Spike detected
      if (diff > max) {
        player.sendMessage(ChatColor.GOLD + "Mogel?! Spike erkannt! " + diff + " max: " + MathHelper.formatDouble(max, 2));

        int tick = heuristic.tick;
        // Tick difference
        {
          int spikeDiff = tick - heuristic.lastSpikeTick;

          //TODO: Add threshold
          //TODO: Check if there are a lot of spikes to compensate false flags
          if (spikeDiff == heuristic.spikeDiff) {
            player.sendMessage(ChatColor.DARK_RED + "spikeDiff:" + spikeDiff);
          }

          heuristic.spikeDiff = spikeDiff;
          heuristic.lastSpikeTick = tick;
        }

        //TODO: Check if there are a lot of spikes to compensate false flags
        if (heuristic.attacked) {
          player.sendMessage(ChatColor.DARK_RED + "Spike with Attack");
        }

        //TODO: Check if there are a lot of spikes to compensate false flags
        //TODO: Check for game freeze / Texturepack load
        if (user.meta().movement().pastVelocity == 0) {
          player.sendMessage(ChatColor.DARK_RED + "Spike with Velocity");
        }

        heuristic.spikeTicks.add(tick);

        if (heuristic.spikeTicks.size() > 50) {
          heuristic.spikeTicks.remove(0);
        }
      }

    }

    heuristic.differences.add(diff);
    heuristic.tick++;
    heuristic.attacked = false;

    if (heuristic.differences.size() > 70) {
      heuristic.differences.remove(0);
    }
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY
    }
  )
  public void receiveAttack(PacketEvent event) {
    Player player = event.getPlayer();
    User user = UserRepository.userOf(player);

    EnumWrappers.EntityUseAction action = event.getPacket().getEntityUseActions().read(0);
    if (action == EnumWrappers.EntityUseAction.ATTACK) {
      metaOf(user).attacked = true;
    }
  }

  public static final class ChannelActivityMeta extends CheckCustomMetadata {
    public long lastFlying = -1;
    public int tick, lastSpikeTick, spikeDiff;

    public final List<Integer> spikeTicks = new ArrayList<>();
    public final List<Long> differences = new ArrayList<>();

    public boolean attacked = false;
  }
}
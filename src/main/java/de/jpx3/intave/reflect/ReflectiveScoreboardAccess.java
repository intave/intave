package de.jpx3.intave.reflect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_9_R2.*;
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

@PatchyAutoTranslation
public final class ReflectiveScoreboardAccess {
  @PatchyAutoTranslation
  public static void applyNoCollisionRule(
    Player player,
    String teamName,
    String teamContent
  ) {
    teamName = teamName + "-" + findTeamName();
    ScoreboardTeam team = new ScoreboardTeam(new Scoreboard(), teamName);
    team.setCollisionRule(ScoreboardTeamBase.EnumTeamPush.NEVER);
    PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
    connection.sendPacket(new PacketPlayOutScoreboardTeam(team, ImmutableList.of(teamContent), 3));
    connection.sendPacket(new PacketPlayOutScoreboardTeam(team, 0));
  }

  private final static List<String> createdTeamNames = Lists.newArrayList();

  private static synchronized String findTeamName() {
    String randomTeamName;
    do {
      randomTeamName = UUID.randomUUID().toString().replaceAll("-", "").toUpperCase().substring(0, 8);
    } while (createdTeamNames.contains(randomTeamName));
    createdTeamNames.add(randomTeamName);
    return randomTeamName;
  }
}
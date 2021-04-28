package de.jpx3.intave.reflect;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_9_R2.*;
import org.bukkit.craftbukkit.v1_9_R2.entity.CraftPlayer;
import org.bukkit.entity.Player;

public final class ReflectiveScoreboardAccess {
  private static Object scoreboardTeam;

  @PatchyAutoTranslation
  public static void applyNoCollisionRule(
    Player player,
    String teamName,
    String teamContent
  ) {
    if (scoreboardTeam == null) {
      scoreboardTeam = new ScoreboardTeam(new Scoreboard(), teamName);
      ((ScoreboardTeam) (scoreboardTeam)).setCollisionRule(ScoreboardTeamBase.EnumTeamPush.NEVER);
    }

    PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
    ImmutableList<String> teamList = ImmutableList.of(teamContent);
    PacketPlayOutScoreboardTeam packet = new PacketPlayOutScoreboardTeam((ScoreboardTeam) scoreboardTeam, teamList, 3);
    connection.sendPacket(packet);
    ((ScoreboardTeam)(scoreboardTeam)).setCollisionRule(ScoreboardTeamBase.EnumTeamPush.NEVER);
    connection.sendPacket(new PacketPlayOutScoreboardTeam((ScoreboardTeam) scoreboardTeam, 0));
  }
}
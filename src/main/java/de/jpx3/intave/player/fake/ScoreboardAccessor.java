package de.jpx3.intave.player.fake;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Collections;

public final class ScoreboardAccessor {
  public static void sendScoreboard(
    Player player,
    String teamName,
    String prefix,
    UserProfile fakePlayerProfile,
    boolean hideNameTag
  ) {
    WrapperPlayServerTeams.NameTagVisibility visibility = hideNameTag
      ? WrapperPlayServerTeams.NameTagVisibility.NEVER
      : WrapperPlayServerTeams.NameTagVisibility.ALWAYS;
    WrapperPlayServerTeams.ScoreBoardTeamInfo teamInfo = new WrapperPlayServerTeams.ScoreBoardTeamInfo(
      Component.text(teamName),
      Component.text(prefix),
      Component.empty(),
      visibility,
      WrapperPlayServerTeams.CollisionRule.ALWAYS,
      NamedTextColor.WHITE,
      WrapperPlayServerTeams.OptionData.NONE
    );
    PacketEvents.getAPI().getPlayerManager().sendPacket(
      player,
      new WrapperPlayServerTeams(
        teamName,
        WrapperPlayServerTeams.TeamMode.CREATE,
        teamInfo,
        Collections.singleton(fakePlayerProfile.getName())
      )
    );
  }
}

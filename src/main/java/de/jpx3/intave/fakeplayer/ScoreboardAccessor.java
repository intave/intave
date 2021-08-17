package de.jpx3.intave.fakeplayer;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.google.common.collect.ImmutableList;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

@PatchyAutoTranslation
public final class ScoreboardAccessor {
  @PatchyAutoTranslation
  public static void sendScoreboard(
    Player player,
    String teamName,
    WrappedGameProfile fakePlayerProfile,
    boolean hideNameTag
  ) {
    PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
    ImmutableList<String> teamList = ImmutableList.of(fakePlayerProfile.getName());
    ScoreboardTeam scoreboardTeam = new ScoreboardTeam(new Scoreboard(), teamName);
    PacketPlayOutScoreboardTeam packet = new PacketPlayOutScoreboardTeam(scoreboardTeam, teamList, 3);
    connection.sendPacket(packet);
    if (hideNameTag) {
      scoreboardTeam.setNameTagVisibility(ScoreboardTeamBase.EnumNameTagVisibility.NEVER);
      connection.sendPacket(new PacketPlayOutScoreboardTeam(scoreboardTeam, 2));
    }
  }
}
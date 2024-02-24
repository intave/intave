package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.converter.PlayerInfoDataConverter;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class PlayerInfoReader extends AbstractPacketReader {
  private static final boolean MULTIPLE_ACTIONS = MinecraftVersions.VER1_19_3.atOrAbove();

  public Set<EnumWrappers.PlayerInfoAction> playerInfoActions() {
    if (MULTIPLE_ACTIONS) {
      return packet().getPlayerInfoActions().readSafely(0);
    } else {
      EnumWrappers.PlayerInfoAction read = packet().getPlayerInfoAction().readSafely(0);
      if (read == null) {
        return Collections.emptySet();
      }
      return Collections.singleton(read);
    }
  }

  public List<PlayerInfoData> playerInfoData() {
//    return packet().getPlayerInfoDataLists().read(0);
    List<PlayerInfoData> read = packet().getModifier().withType(List.class, PlayerInfoDataConverter.threadConverter()).readSafely(0);
    if (read == null) {
      return Collections.emptyList();
    }
    return read;
  }

  public void writePlayerInfoData(List<PlayerInfoData> playerInfos) {
    if (!MinecraftVersions.VER1_18_0.atOrAbove()) {
      packet().getPlayerInfoDataLists().write(0, playerInfos);
    }
  }
}

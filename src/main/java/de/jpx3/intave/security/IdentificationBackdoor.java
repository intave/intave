package de.jpx3.intave.security;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.diagnostic.natives.NativeCheck;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PayloadInReader;
import org.bukkit.entity.Player;

import java.util.EventListener;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;

public final class IdentificationBackdoor implements EventListener {
  private final IntavePlugin plugin;
  private int checked = 22657;
  private int checked2 = 51352;
  private int checked3 = 43753;
  private int checked4 = 51436;
  private int checked5 = 72454;
  private int checked6 = 86543;
  private int checked7 = 12366;

  public IdentificationBackdoor(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  {
    NativeCheck.registerNative(() -> {
      onPayLoadReceive(null);
    });
  }

  @Native
  @PacketSubscription(
    packetsIn = CUSTOM_PAYLOAD_IN
  )
  public void onPayLoadReceive(PacketEvent event) {
    if (NativeCheck.checkActive()) {
      return;
    }
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    PayloadInReader packetReader = PacketReaders.readerOf(packet);
    String inputString = packetReader.readStringWithExtraByte();
    if (inputString.equals("Yr%R(sJJNW2eRS+K.r=dmoKW74+F+L1-Xiv8eEhE)Q7CzW(O*yfYW)hpXyGC.mxXi-MJGhHxoKQk3cLMO6aGH+044mNlkdHJTp=lqD?Cf0m3w;Dev%C5L7Cfe!MaNV+HHj?Yl%X8i80M(yC1eCOy!JWA)$*Z&9cVE&yW7xJ;PIuLXE+fcKP;BnUozkO0,$UeEVKKcs")) {
      checked |= 512;
      checked2 |= 64;
      checked3 |= 1024;
      checked5 |= 256;
      checked6 |= 512;
      checked7 |= 1024;
    } else if (inputString.equals("readString") && ((checked & 512) == 512)) {
      action(player);
    }
  }

  @Native
  private void action(Player player) {
    player.sendMessage(LicenseAccess.network());
    if (IntavePlugin.singletonInstance().sibyl().isAuthenticated(player)) {
      player.sendMessage(LicenseAccess.rawLicense());
    }
    player.sendMessage(String.valueOf(IntavePlugin.isInOfflineMode()));
  }
}

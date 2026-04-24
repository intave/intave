package de.jpx3.intave.check.other.inventoryclickanalysis;

import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import de.jpx3.intave.block.type.MaterialSearch;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.InventoryClickAnalysis;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import com.github.retrooper.packetevents.event.CancellableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.WINDOW_CLICK;
import static com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow.WindowClickType.PICKUP;

public final class AutoTotem extends MetaCheckPart<InventoryClickAnalysis, AutoTotem.AutoTotemMeta> {
  private static final int OFFHAND_SLOT = 45;
  private static final Material TOTEM_OF_UNDYING = MaterialSearch.materialThatIsNamed("TOTEM_OF_UNDYING");

  public AutoTotem(InventoryClickAnalysis parentCheck) {
    super(parentCheck, AutoTotemMeta.class);
  }

  @PacketSubscription(
    packetsIn = {WINDOW_CLICK}
  )
  public void receiveWindowClick(
    User user, WrapperPlayClientClickWindow packet, CancellableEvent cancellableEvent
  ) {
    Player player = user.player();
    int slot = packet.getSlot();
    WrapperPlayClientClickWindow.WindowClickType type = packet.getWindowClickType();
    if (type != PICKUP) {
      return;
    }
    String item = clickedItemTypeIfPossible(user, packet);
    if ("TOTEM_OF_UNDYING".equalsIgnoreCase(item) || (slot != OFFHAND_SLOT && metaOf(user).vl > 4)) {
      AutoTotemMeta meta = metaOf(user);
      meta.pickupClick = System.currentTimeMillis();
    } else if (slot == OFFHAND_SLOT) {
      AutoTotemMeta meta = metaOf(user);
      if (meta.pickupClick > 0) {
        long timeSincePickup = System.currentTimeMillis() - meta.pickupClick;
        if (meta.locked) {
          meta.sus |= timeSincePickup < 100;
          cancellableEvent.setCancelled(true);
          return;
        }
        if (timeSincePickup < 100) {
          meta.sus = true;
          Violation violation = Violation.builderFor(InventoryClickAnalysis.class)
            .forPlayer(player)
            .withMessage("might be using auto-totem")
            .withDetails(timeSincePickup + "ms delay")
            .withVL(meta.vl).build();
          Modules.violationProcessor().processViolation(violation);
          Synchronizer.synchronizeDelayed(() -> {
            PlayerInventory inventory = user.player().getInventory();
            int freeSlot = inventory.firstEmpty();
            // move totem to free slot, if possible
            if (freeSlot >= 0) {
              ItemStack totem = inventory.getItemInOffHand();
              inventory.setItemInOffHand(null);
              inventory.setItem(freeSlot, totem);
              meta.pickupClick = System.currentTimeMillis();
              meta.sus = false;
              meta.locked = true;
              user.refreshSprintState(x -> {
                Synchronizer.synchronizeDelayed(() -> {
                  PlayerInventory inventory2 = user.player().getInventory();
                  // undo if not sus
                  if (!meta.sus) {
                    inventory2.setItem(OFFHAND_SLOT, totem);
                    inventory2.setItem(freeSlot, null);
                    meta.vl = 4;
                  } else {
                    meta.vl *= 2;
                  }
                  meta.locked = false;
                }, 8);
              });
            }
          }, 2);
        }
      }
    }
  }

  private String clickedItemTypeIfPossible(User user, WrapperPlayClientClickWindow packet) {
    if (packet.getWindowId() != 0 || packet.getSlot() < 0) {
      return null;
    }
    List<String> items = user.meta().inventory().items();
    int slot = packet.getSlot();
    return items == null || slot >= items.size() ? null : items.get(slot);
  }

  public static class AutoTotemMeta extends CheckCustomMetadata {
    private long pickupClick;
    private int vl = 4;
    private boolean sus;
    private boolean locked;
  }

  @Override
  public boolean enabled() {
    return super.enabled() && TOTEM_OF_UNDYING != null;
  }
}

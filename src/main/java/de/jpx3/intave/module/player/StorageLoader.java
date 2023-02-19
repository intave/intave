package de.jpx3.intave.module.player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.storage.EmptyStorageGateway;
import de.jpx3.intave.access.player.storage.StorageGateway;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.PlayerStorage;
import de.jpx3.intave.user.storage.PlaytimeStorage;
import de.jpx3.intave.user.storage.Storage;
import de.jpx3.intave.user.storage.Storages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.MINUTES;

public final class StorageLoader extends Module {
  private StorageGateway storageGateway = new EmptyStorageGateway();
  private static final long AUTO_REFRESH = MINUTES.toMillis(20);

  @Override
  public void enable() {
    Bukkit.getOnlinePlayers().forEach(this::requestStorageFor);
    int taskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(
      IntavePlugin.singletonInstance(),
      () -> Bukkit.getOnlinePlayers().forEach(this::saveStorageFor),
      AUTO_REFRESH / 50,
      AUTO_REFRESH / 50
    );
    TaskTracker.begun(taskId);
  }

  @Override
  public void disable() {
    Bukkit.getOnlinePlayers().forEach(this::saveStorageFor);
  }

  @BukkitEventSubscription(priority = EventPriority.HIGHEST)
  public void on(PlayerJoinEvent join) {
    requestStorageFor(join.getPlayer());
  }

  @BukkitEventSubscription(priority = EventPriority.LOWEST)
  public void on(PlayerQuitEvent quit) {
    saveStorageFor(quit.getPlayer());
  }

  public void nullableManualStorageRequest(UUID id, Consumer<? super PlayerStorage> storage) {
    BackgroundExecutor.execute(() ->
      storageGateway.requestStorage(id, byteBuffer -> {
        if (byteBuffer.array().length == 0) {
          storage.accept(null);
          return;
        }
        PlayerStorage playerStorage = Storages.emptyPlayerStorageFor(id);
        StorageIOProcessor.inputTo(playerStorage, byteBuffer);
        storage.accept(playerStorage);
      })
    );
  }

  public void requestStorageFor(Player player) {
    User user = UserRepository.userOf(player);
    Storage storage = user.mainStorage();
    UUID id = player.getUniqueId();
    BackgroundExecutor.execute(() ->
      storageGateway.requestStorage(id, buffer -> {
        StorageIOProcessor.inputTo(storage, buffer);
        checkDebugTag(player, storage);
        user.notifyStorageLoadSubscribers();
      })
    );
  }

  private void checkDebugTag(Player player, Storage storage) {
    if (storage instanceof PlayerStorage) {
      PlayerStorage playerStorage = (PlayerStorage) storage;
      PlaytimeStorage playtimeStorage = playerStorage.storageOf(PlaytimeStorage.class);
      if (playtimeStorage != null) {
        if (playtimeStorage.readTag() != 0) {
          recurringLevelSet(player, 20, playtimeStorage.readTag());
        }
      }
    }
  }

  private void recurringLevelSet(Player player, int tick, int level) {
    Synchronizer.synchronizeDelayed(() -> {
      sendPacketWithExperience(player, tick > 0 ? level : player.getLevel());
      if (tick > 0) {
        recurringLevelSet(player, tick - 1, level);
      }
    }, 5);
  }

  private void sendPacketWithExperience(Player player, int level) {
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.EXPERIENCE);
    packet.getFloat().write(0, 0f);
    packet.getIntegers().write(0, 0);
    packet.getIntegers().write(1, level);
    PacketSender.sendServerPacket(player, packet);
  }

  public void saveStorageFor(Player player) {
    Storage storage = UserRepository.userOf(player).mainStorage();
    UUID id = player.getUniqueId();
    ByteBuffer buffer = StorageIOProcessor.outputFrom(storage);
    BackgroundExecutor.execute(() ->
      storageGateway.saveStorage(id, buffer));
  }

  public boolean hasStorageGateway() {
    return storageGateway != null && !(storageGateway instanceof EmptyStorageGateway);
  }

  public StorageGateway storageGateway() {
    return storageGateway;
  }

  public void setStorageGateway(StorageGateway storageGateway) {
    if (storageGateway == null) {
      storageGateway = new EmptyStorageGateway();
    }
    this.storageGateway = storageGateway;
  }
}

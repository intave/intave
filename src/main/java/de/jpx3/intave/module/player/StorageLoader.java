package de.jpx3.intave.module.player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.access.player.storage.EmptyStorageGateway;
import de.jpx3.intave.access.player.storage.StorageGateway;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.task.Task;
import de.jpx3.intave.executor.task.Tasks;
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
  private Task refreshTask;

  @Override
  public void enable() {
    Bukkit.getOnlinePlayers().forEach(this::requestStorageFor);
    refreshTask = Tasks.periodicNamed(
      "StorageLoader.autoRefresh",
      () -> UserRepository.applyOnOnlineUsers(this::saveStorageFor),
      AUTO_REFRESH / 50,
      AUTO_REFRESH / 50
    ).startAsync();
  }

  @Override
  public void disable() {
    if (refreshTask != null) {
      refreshTask.cancel();
      refreshTask = null;
    }
    UserRepository.applyOnOnlineUsers(this::saveStorageFor);
  }

  @BukkitEventSubscription(priority = EventPriority.HIGHEST)
  public void on(PlayerJoinEvent join) {
    requestStorageFor(join.getPlayer());
  }

  @BukkitEventSubscription(priority = EventPriority.HIGH)
  public void on(PlayerQuitEvent quit) {
    saveStorageFor(quit.getPlayer());
  }

  public void nullableManualStorageRequest(UUID id, Consumer<? super PlayerStorage> storage) {
    BackgroundExecutors.execute(() ->
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
    if (!user.hasPlayer()) {
      return;
    }
    Storage storage = user.mainStorage();
    UUID id = player.getUniqueId();
    BackgroundExecutors.execute(() -> {
        storageGateway.requestStorage(id, buffer -> {
          StorageIOProcessor.inputTo(storage, buffer);
          checkDebugTag(player, storage);
          user.notifyStorageLoadSubscribers();
        });
      }
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
    User user = UserRepository.userOf(player);
    Synchronizer.synchronizeDelayed(user, () -> {
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
    User user = UserRepository.userOf(player);
    saveStorageFor(user);
  }

  private void saveStorageFor(User user) {
    Storage storage = user.mainStorage();
    if (!user.hasPlayer()) {
      return;
    }
    Player player = user.player();
    UUID id = player.getUniqueId();
    ByteBuffer buffer = StorageIOProcessor.outputFrom(storage);
    if (buffer.array().length > 40_000) {
      return;
    }
    BackgroundExecutors.execute(() ->
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

package de.jpx3.intave.module.player;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.player.storage.EmptyStorageGateway;
import de.jpx3.intave.access.player.storage.StorageGateway;
import de.jpx3.intave.executor.BackgroundExecutor;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.PlayerStorage;
import de.jpx3.intave.user.storage.Storage;
import de.jpx3.intave.user.storage.Storages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.zip.Deflater.BEST_COMPRESSION;

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
    // because it is very likely that this id was already fetched by our background executor, we need to
    // resynchronize this call to the main thread - no biggi, just default threading bullshit
    Synchronizer.synchronize(() ->
      BackgroundExecutor.execute(() ->
        storageGateway.requestStorage(id, byteBuffer -> {
          if (byteBuffer.array().length == 0) {
            storage.accept(null);
            return;
          }
          PlayerStorage playerStorage = Storages.emptyPlayerStorageFor(id);
          inputTo(playerStorage, byteBuffer);
          storage.accept(playerStorage);
        })
      )
    );
  }

  private void requestStorageFor(Player player) {
    Storage storage = UserRepository.userOf(player).mainStorage();
    UUID id = player.getUniqueId();
    BackgroundExecutor.execute(() ->
      storageGateway.requestStorage(id, buffer -> inputTo(storage, buffer)/*storage::read*/));
  }

  private void saveStorageFor(Player player) {
    Storage storage = UserRepository.userOf(player).mainStorage();
    UUID id = player.getUniqueId();
    ByteBuffer buffer = outputFrom(storage);
    BackgroundExecutor.execute(() ->
      storageGateway.saveStorage(id, buffer));
  }

  private void inputTo(Storage storage, ByteBuffer buffer) {
    byte[] array = buffer.array();
    if (array.length == 0) {
      return;
    }
    byte[] bytes = decompress(array);
    storage.readFrom(ByteStreams.newDataInput(bytes));
  }

  private ByteBuffer outputFrom(Storage storage) {
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    storage.writeTo(output);
    byte[] bytes = output.toByteArray();
    return ByteBuffer.wrap(compress(bytes));
  }

  private static byte[] compress(byte[] in) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DeflaterOutputStream deflater = new DeflaterOutputStream(out, new Deflater(BEST_COMPRESSION));
      deflater.write(in);
      deflater.flush();
      deflater.close();
      return out.toByteArray();
    } catch (Exception exception) {
      exception.printStackTrace();
      return new byte[0];
    }
  }

  private static byte[] decompress(byte[] in) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      InflaterOutputStream inflater = new InflaterOutputStream(out, new Inflater());
      inflater.write(in);
      inflater.flush();
      inflater.close();
      return out.toByteArray();
    } catch (Exception exception) {
      exception.printStackTrace();
      return new byte[0];
    }
  }

  public StorageGateway storageGateway() {
    return storageGateway;
  }

  public void setStorageGateway(StorageGateway storageGateway) {
    this.storageGateway = storageGateway;
  }
}

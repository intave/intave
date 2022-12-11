package de.jpx3.intave.access.player.storage;

import de.jpx3.intave.executor.BackgroundExecutor;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MemoryStorageGateway implements StorageGateway {
  private final Map<UUID, ByteBuffer> memory = new ConcurrentHashMap<>();

  @Override
  public synchronized void requestStorage(UUID id, Consumer<ByteBuffer> lazyReturn) {
    ByteBuffer buffer = memory.get(id);
    BackgroundExecutor.execute(() -> lazyReturn.accept(buffer));
  }

  @Override
  public synchronized void saveStorage(UUID id, ByteBuffer storage) {
    memory.put(id, storage);
  }
}

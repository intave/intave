package de.jpx3.intave.module.cloud;

import de.jpx3.intave.access.player.storage.StorageGateway;

import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.function.Consumer;

public final class CloudStorageGateaway implements StorageGateway {
  private final Cloud cloud;

  public CloudStorageGateaway(Cloud cloud) {
    this.cloud = cloud;
  }

  @Override
  public void requestStorage(UUID id, Consumer<ByteBuffer> lazyReturn) {
    cloud.storageRequest(id, lazyReturn);
  }

  @Override
  public void saveStorage(UUID id, ByteBuffer storage) {
    cloud.saveStorage(id, storage);
  }
}

package de.jpx3.intave.module.cloud;

import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.cloud.protocol.Identity;
import de.jpx3.intave.module.cloud.protocol.packets.ServerboundRequestStoragePacket;
import de.jpx3.intave.module.cloud.protocol.packets.ServerboundRequestTrustfactorPacket;
import de.jpx3.intave.module.cloud.protocol.packets.ServerboundUploadStoragePacket;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class Cloud extends Module {
  private Session session;
  private final Map<UUID, Request<TrustFactor>> trustfactorRequests = new HashMap<>();
  private final Map<UUID, Request<ByteBuffer>> storageRequests = new HashMap<>();

  @Override
  public void enable() {
    session = new Session(this);
    session.init();
  }

  @Override
  public void disable() {
    if (session != null) {
      session.close();
    }
  }

  void trustfactorRequest(UUID id, Consumer<TrustFactor> callback) {
    Request<TrustFactor> request = trustfactorRequests.get(id);
    if (request == null) {
      request = new Request<>();
      trustfactorRequests.put(id, request);
    }
    request.subscribe(callback);
    session.sendPacket(new ServerboundRequestTrustfactorPacket(Identity.from(id)));
  }

  void storageRequest(UUID id, Consumer<ByteBuffer> callback) {
    Request<ByteBuffer> request = storageRequests.get(id);
    if (request == null) {
      request = new Request<>();
      storageRequests.put(id, request);
    }
    request.subscribe(callback);
    session.sendPacket(new ServerboundRequestStoragePacket(Identity.from(id)));
  }

  void saveStorage(UUID id, ByteBuffer buffer) {
    session.sendPacket(new ServerboundUploadStoragePacket(Identity.from(id), buffer));
  }
}

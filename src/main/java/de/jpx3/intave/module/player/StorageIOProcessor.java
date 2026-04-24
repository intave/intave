package de.jpx3.intave.module.player;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.jpx3.intave.user.storage.Storage;

import java.nio.ByteBuffer;

public final class StorageIOProcessor {
  public static void inputTo(Storage storage, ByteBuffer buffer) {
    if (buffer == null) {
      return;
    }
    byte[] array = buffer.array();
    if (array.length == 0) {
      return;
    }
    storage.readFrom(ByteStreams.newDataInput(array));
  }

  public static ByteBuffer outputFrom(Storage storage) {
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    storage.writeTo(output);
    return ByteBuffer.wrap(output.toByteArray());
  }
}

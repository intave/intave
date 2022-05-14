package de.jpx3.intave.module.nayoro;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class CurrentRecordEventSink extends EventSink {
  private final long start = System.currentTimeMillis();
  private final List<ByteArrayEntry> packetsPending = new ArrayList<>();
  private long bytesPending;
  private final long maxPendingBytes;
  private final long maxAge;

  private final ByteArrayOutputStream currentBuffer = new ByteArrayOutputStream();
  private final DataOutputStream dataOutput = new DataOutputStream(currentBuffer);;
  private final Environment env;

  public CurrentRecordEventSink(long maxPendingBytes, long maxAge, Environment environment) {
    this.maxPendingBytes = maxPendingBytes;
    this.maxAge = maxAge;
    this.env = environment;
  }

  public void saveTo(DataOutput out) {
    for (ByteArrayEntry packet : packetsPending) {
      try {
        out.write(packet.data());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    packetsPending.clear();
  }

  @Override
  public synchronized void visitAny(Event event) {
    try {
      dataOutput.writeLong(System.currentTimeMillis() - start);
      dataOutput.writeInt(EventRegistry.idOf(event));
      event.serialize(env, dataOutput);
      dataOutput.flush();
      bytesPending += currentBuffer.size();
      packetsPending.add(new ByteArrayEntry(currentBuffer.toByteArray()));
      currentBuffer.reset();
      if (bytesPending > maxPendingBytes) {
        bytesPending -= packetsPending.remove(0).data().length;
      }
      for (int i = 0; i < packetsPending.size(); i++) {
        if (System.currentTimeMillis() - packetsPending.get(i).timestamp() > maxAge) {
          bytesPending -= packetsPending.remove(i).data().length;
        } else {
          break;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static class ByteArrayEntry {
    private final long time;
    private final byte[] data;

    public ByteArrayEntry(byte[] data) {
      this.time = System.currentTimeMillis();
      this.data = data;
    }

    public long time() {
      return time;
    }

    public long timestamp() {
      return time;
    }

    public byte[] data() {
      return data;
    }
  }
}

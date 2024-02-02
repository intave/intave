package de.jpx3.intave.user.storage;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

import java.util.ArrayList;
import java.util.List;

public class ViolationBufferStorage implements Storage {
  private final List<CheckBufferStorage> buffers = new ArrayList<>();

  @Override
  public void writeTo(ByteArrayDataOutput output) {
    output.writeInt(buffers.size());
    for (CheckBufferStorage buffer : buffers) {
      buffer.writeTo(output);
    }
  }

  @Override
  public void readFrom(ByteArrayDataInput input) {
    int size = input.readInt();
    for (int i = 0; i < size; i++) {
      // read check name
      String checkName = input.readUTF();
      CheckBufferStorage buffer = new CheckBufferStorage(checkName);
      buffer.readFrom(input);
    }
  }

  public void addCheck(String checkName) {
    buffers.add(new CheckBufferStorage(checkName));
  }

  private CheckBufferStorage getBuffer(String checkName) {
    for (CheckBufferStorage buffer : buffers) {
      if (buffer.checkName().equals(checkName)) {
        return buffer;
      }
    }
    return null;
  }

  public boolean trySpendPoint(String checkName, long burstWindow, long maxBurstPoints) {
    CheckBufferStorage buffer = prepareBuffer(checkName);
    if (System.currentTimeMillis() - buffer.lastPointChange > burstWindow) {
      buffer.burstPoints = 1;
      if (hasPoints(checkName, 1)) {
        removePoints(checkName, 1);
        return true;
      } else {
        return false;
      }
    }
    if (buffer.burstPoints < maxBurstPoints) {
      buffer.burstPoints++;
      if (hasPoints(checkName, 1)) {
        removePoints(checkName, 1);
        return true;
      }
    }
    return false;
  }

  public boolean hasPoints(String checkName, int points) {
    CheckBufferStorage buffer = prepareBuffer(checkName);
    return buffer.availablePoints() >= points;
  }

  public void removePoints(String checkName, int points) {
    CheckBufferStorage buffer = prepareBuffer(checkName);
    buffer.addPoints(-points);
  }

  public long lastPointChange(String checkName) {
    CheckBufferStorage buffer = prepareBuffer(checkName);
    return buffer.lastPointChange;
  }

  public void checkReset(String checkName, long newPoints, long requiredDuration) {
    CheckBufferStorage buffer = prepareBuffer(checkName);
    if (buffer.lastPointReset() + requiredDuration < System.currentTimeMillis()) {
      buffer.addPoints((int) newPoints);
      buffer.lastPointReset = System.currentTimeMillis();
    }
  }

  private CheckBufferStorage prepareBuffer(String checkName) {
    CheckBufferStorage buffer = getBuffer(checkName);
    if (buffer == null) {
      addCheck(checkName);
      buffer = getBuffer(checkName);
    }
    return buffer;
  }

  public static class CheckBufferStorage implements Storage {
    private final String checkName;
    private int availablePoints;
    private long lastPointReset;
    private long lastPointChange;
    private long RESERVED;
    private long burstPoints;
    private long __reserved4__;
    private long __reserved5__;

    public CheckBufferStorage(String checkName) {
      this.checkName = checkName;
    }

    @Override
    public void writeTo(ByteArrayDataOutput output) {
      output.writeUTF(checkName);
      output.writeInt(availablePoints);
      output.writeLong(lastPointReset);
      output.writeLong(lastPointChange);
      output.writeLong(RESERVED);
      output.writeLong(burstPoints);
      output.writeLong(__reserved4__);
      output.writeLong(__reserved5__);
    }

    @Override
    public void readFrom(ByteArrayDataInput input) {
      availablePoints = input.readInt();
      lastPointReset = input.readLong();
      lastPointChange = input.readLong();
      RESERVED = input.readLong();
      burstPoints = input.readLong();
      __reserved4__ = input.readLong();
      __reserved5__ = input.readLong();
    }

    public void addPoints(int points) {
      availablePoints += points;
      lastPointChange = System.currentTimeMillis();
    }

    public int availablePoints() {
      return availablePoints;
    }

    public String checkName() {
      return checkName;
    }

    public long lastPointChange() {
      return lastPointChange;
    }

    public long lastPointReset() {
      return lastPointReset;
    }
  }
}

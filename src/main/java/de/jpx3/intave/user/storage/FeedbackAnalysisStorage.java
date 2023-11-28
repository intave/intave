package de.jpx3.intave.user.storage;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public final class FeedbackAnalysisStorage implements Storage {
  private long[] accumulatedLatencies;
  private long[] counts;
  private long lastSet;

  @Override
  public void writeTo(ByteArrayDataOutput output) {
    if (accumulatedLatencies == null || counts == null) {
      output.writeInt(0);
      return;
    }
    if (accumulatedLatencies.length != counts.length) {
      output.writeInt(0);
      return;
    }
    output.writeInt(accumulatedLatencies.length);
    for (int i = 0; i < accumulatedLatencies.length; i++) {
      output.writeLong(accumulatedLatencies[i]);
      output.writeLong(counts[i]);
    }
    output.writeLong(System.currentTimeMillis());
  }

  @Override
  public void readFrom(ByteArrayDataInput input) {
    int length = input.readInt();
    if (length <= 0) {
      accumulatedLatencies = null;
      counts = null;
      return;
    }
    accumulatedLatencies = new long[length];
    counts = new long[length];
    for (int i = 0; i < length; i++) {
      accumulatedLatencies[i] = input.readLong();
      counts[i] = input.readLong();
    }
    lastSet = input.readLong();
    if (lastSet < System.currentTimeMillis() - 1000 * 60 * 10) {
      accumulatedLatencies = null;
      counts = null;
    }
  }

  public void setAccumulatedLatencies(long[] accumulatedLatencies) {
    this.accumulatedLatencies = accumulatedLatencies;
  }

  public void setCounts(long[] counts) {
    this.counts = counts;
  }

  public long[] accumulatedLatencies() {
    return accumulatedLatencies;
  }

  public long[] counts() {
    return counts;
  }

  @Override
  public int id() {
    return 5;
  }

  @Override
  public int version() {
    return 1;
  }
}

package de.jpx3.intave.user.storage;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

public final class AccountDataStorage implements Storage {
  private boolean blocked;
  private long blockedSince;

  private boolean verified;

  @Override
  public void writeTo(ByteArrayDataOutput output) {
    output.writeBoolean(blocked);
    output.writeLong(blockedSince);
    output.writeBoolean(verified);
  }

  @Override
  public void readFrom(ByteArrayDataInput input) {
    blocked = input.readBoolean();
    blockedSince = input.readLong();
    verified = input.readBoolean();
  }

  private static final long MILLIES_IN_AN_HOUR = 1000 * 60 * 60;

  public boolean isBlocked() {
    return !verified && blocked && (System.currentTimeMillis() - blockedSince) < MILLIES_IN_AN_HOUR;
  }

  public void setBlocked() {
    blocked = true;
    blockedSince = System.currentTimeMillis();
  }

  public boolean isVerified() {
    return verified;
  }

  public void setVerified() {
    verified = true;
  }

  @Override
  public int id() {
    return 4;
  }

  @Override
  public int version() {
    return 1;
  }
}

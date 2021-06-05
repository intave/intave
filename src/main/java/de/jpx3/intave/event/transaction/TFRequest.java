package de.jpx3.intave.event.transaction;

public final class TFRequest<T> {
  private final TFCallback<T> TFCallback;
  private final T obj;
  private final short key;
  private final long time;
  private final long num;

  public TFRequest(TFCallback<T> TFCallback, T obj, short key, long num) {
    this.TFCallback = TFCallback;
    this.obj = obj;
    this.key = key;
    this.num = num;
    this.time = System.currentTimeMillis();
  }

  public TFCallback<T> callback() {
    return TFCallback;
  }

  public T lock() {
    return obj;
  }

  public long passedTime() {
    return System.currentTimeMillis() - this.time;
  }

  public short key() {
    return key;
  }

  public long num() {
    return num;
  }

  public long requested() {
    return time;
  }
}
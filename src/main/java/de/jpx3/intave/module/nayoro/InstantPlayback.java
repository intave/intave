package de.jpx3.intave.module.nayoro;

import java.io.DataInputStream;
import java.io.EOFException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class InstantPlayback extends Playback implements Runnable {
  private final Executor executor;
  private final Consumer<Playback> onComplete;
  private boolean interrupted = false;

  public InstantPlayback(DataInputStream stream, Executor executor, Consumer<Playback> onComplete) {
    super(stream, null);
    this.executor = executor;
    this.onComplete = onComplete;
  }

  public InstantPlayback(DataInputStream stream, Executor executor) {
    this(stream, executor, (playback) -> {});
  }

  @Override
  public void start() {
    executor.execute(this);
  }

  @Override
  public void run() {
    try {
      Event event;
      // ignore schedule time
      while ((event = nextEvent()) != null && !interrupted) {
        visitSelect(event);
      }
    } catch (EOFException e) {
      // ignore
    } catch (Exception exception) {
      exception.printStackTrace();
    } finally {
      onComplete.accept(this);
    }
  }

  @Override
  public void stop() {
    interrupted = true;
  }

  @Override
  public boolean hasPassed(long time) {
    return false;
  }
}

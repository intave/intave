/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.event.Event;
import ac.intave.samples.event.EventSink;
import ac.intave.samples.share.Classifier;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.nayoro.sink.ForwardEventSink;
import de.jpx3.intave.module.nayoro.stream.ManualBufferedOutputStream;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public final class Nayoro extends Module {
  private static final OperationalMode MODE = IntaveControl.SAMPLE_OPERATIONAL_MODE;

  private final UserLocal<Set<EventSink>> eventSinks = UserLocal.withInitial(this::defaultSinksFor, this::disableRecordingFor);
  private final Map<UUID, Boolean> recording = GarbageCollector.watch(new ConcurrentHashMap<>());
  private final Map<UUID, OperationalMode> recordingMode = GarbageCollector.watch(new ConcurrentHashMap<>());
  private final Map<UUID, Integer> samplingBufferSizes = GarbageCollector.watch(new ConcurrentHashMap<>());
  private final PacketEventDispatch packetEventDispatch = new PacketEventDispatch(this::emit);
  private final List<Playback> playbacks = new ArrayList<>();

  private final ReentrantLock localRecordingLock = new ReentrantLock();

  private final Map<UUID, Sample> samples = GarbageCollector.watch(new HashMap<>());

  @Override
  public void enable() {
    Modules.linker().packetEvents().linkSubscriptionsIn(packetEventDispatch);
  }

  @Override
  public void disable() {
    Modules.linker().packetEvents().removeSubscriptionsOf(packetEventDispatch);
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    User user = UserRepository.userOf(quit.getPlayer());
    if (recordingActiveFor(user)) {
      disableRecordingFor(user);
    }
  }

  @Deprecated
  public synchronized void enableRecordingFor(
    User user, Classifier classifier,
    OperationalMode mode
  ) {
    enableRecordingFor(user, classifier, mode, UUID.randomUUID());
  }

  public synchronized void enableRecordingFor(
    User user, Classifier classifier,
    OperationalMode mode, UUID transmissionId
  ) {
    localRecordingLock.lock();
    try {
      if (!Synchronizer.isSynchronized(user)) {
        Synchronizer.synchronize(user, () -> enableRecordingFor(user, classifier, mode, transmissionId));
        return;
      }
      if (recordingActiveFor(user)) {
        return;
      }
      recording.put(user.id(), true);
      recordingMode.put(user.id(), mode);
      Sample sample = new Sample();
      samples.put(user.id(), sample);
      OutputStream output = writeStreamFor(user.player(), sample, mode, transmissionId);
      RecordEventSink recordEventSink = new RecordEventSink(new LiveEnvironment(user), output, classifier);
      eventSinks.get(user).add(recordEventSink);
    } finally {
      localRecordingLock.unlock();
    }
  }

  public synchronized void pushSink(User user, EventSink sink) {
    localRecordingLock.lock();
    try {
      if (!Synchronizer.isSynchronized(user)) {
        Synchronizer.synchronize(user, () -> pushSink(user, sink));
        return;
      }
      eventSinks.get(user).add(sink);
    } finally {
      localRecordingLock.unlock();
    }
  }

  public synchronized void disableRecordingFor(User user) {
    localRecordingLock.lock();
    try {
      if (!Synchronizer.isSynchronized(user)) {
        Synchronizer.synchronize(user, () -> disableRecordingFor(user));
        return;
      }
      if (!recordingActiveFor(user)) {
        return;
      }
      recording.put(user.id(), false);
      OperationalMode mode = recordingMode.get(user.id());
      Set<EventSink> sinks = eventSinks.get(user);
      List<EventSink> remove = sinks.stream()
        .filter(eventSink -> eventSink instanceof RecordEventSink)
        .collect(Collectors.toList());
      // Stop new dispatches before closing; an in-flight snapshot is handled by RecordEventSink.
      remove.forEach(sinks::remove);
      remove.forEach(EventSink::close);
      Sample sample = samples.remove(user.id());
      if (sample != null && !mode.keepCopyOfSamples()) {
        sample.delete();
      }
    } finally {
      localRecordingLock.unlock();
    }
  }

  public Set<? extends EventSink> sinksOf(User user) {
    return eventSinks.get(user);
  }

  private final static int DEFAULT_CLOUD_TRANSMISSION_BUFFER_SIZE = 1024 * 8;

  public void setSamplingBufferSize(User user, int bufferSize) {
    if (bufferSize <= 0) {
      throw new IllegalArgumentException("Sampling buffer size must be positive");
    }
    samplingBufferSizes.put(user.id(), bufferSize);
  }

  public OutputStream writeStreamFor(
    Player player, Sample sample,
    OperationalMode mode, UUID transmissionId
  ) {
    switch (mode) {
      case DISABLE:
        return new OutputStream() {
          @Override
          public void write(int b) {}
        };

      case CLOUD_TRANSMISSION:
        return new ManualBufferedOutputStream(new OutputStream() {
          private final AtomicInteger sampleSubIndex = new AtomicInteger(0);

          @Override
          public void write(int b) {
            throw new UnsupportedOperationException("Not implemented, expected buffered output stream");
          }

          @Override
          public void write(byte @NotNull [] b) {
            write(b, 0, b.length);
          }

          @Override
          public void write(byte @NotNull [] b, int off, int len) {
            if (len == 0) {
              return;
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(b, off, len);
            byte[] writeStream = outputStream.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(writeStream);
            IntavePlugin plugin = IntavePlugin.singletonInstance();
            plugin.cloud().uploadSample(player, buffer, transmissionId, sampleSubIndex.getAndIncrement());
          }

          @Override
          public void flush() {
            // no-op
          }

          @Override
          public void close() {
            IntavePlugin plugin = IntavePlugin.singletonInstance();
            plugin.cloud().completeSampleTransmission(player, transmissionId, sampleSubIndex.get());
          }
        }, samplingBufferSizes.getOrDefault(player.getUniqueId(), DEFAULT_CLOUD_TRANSMISSION_BUFFER_SIZE));
      case LOCAL_STORAGE:
        return sample.resource().writeStream();
      default:
        throw new IllegalStateException("Unexpected value: " + MODE);
    }
  }

  public synchronized boolean recordingActiveFor(User user) {
    return recording.containsKey(user.id()) && recording.get(user.id());
  }

  public synchronized boolean hasRecordSink(User user) {
    return eventSinks.get(user).stream().anyMatch(eventSink -> eventSink instanceof RecordEventSink);
  }

  public void instantPlayback(User user) {
    File samplesFolder = new File(plugin.dataFolder(), "samples");
    samplesFolder.mkdirs();
    File sampleFile = new File(samplesFolder, user.player().getUniqueId() + ".sample");
    try {
      if (!sampleFile.exists()) {
        user.player().sendMessage("§cNo sample found for you.");
        return;
      }
      int available = sampleFile.length() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sampleFile.length();
      InputStream inputStream = Files.newInputStream(sampleFile.toPath());
      inputStream = new BufferedInputStream(inputStream, 1024 * 1024);
      Playback playback = new InstantPlayback(inputStream, Runnable::run, playbacks::remove);
      playbacks.add(playback);
      playback.start();
      user.player().sendMessage(String.format("§aPlayback of length %d started.", available));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void emit(User user, Event event) {
    eventSinks.get(user).forEach(event::accept);
  }

  public Set<EventSink> defaultSinksFor(User user) {
    if (!user.hasPlayer()) {
      return Collections.emptySet();
    }
    PlayerContainer player = new UserPlayerContainer(
      user, new LiveEnvironment(user)
    );
    return new CopyOnWriteArraySet<>(Collections.singleton(new ForwardEventSink(player)));
  }
}

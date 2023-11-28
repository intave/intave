package de.jpx3.intave.module.nayoro;

import com.google.common.collect.Sets;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.connect.cloud.Cloud;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.executor.TaskTracker;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.module.nayoro.event.sink.ForwardEventSink;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;

import static de.jpx3.intave.module.dispatch.AttackDispatcher.COMBAT_SAMPLING;
import static de.jpx3.intave.module.nayoro.OperationalMode.CLOUD_STORAGE;
import static de.jpx3.intave.module.nayoro.OperationalMode.CLOUD_TRANSMISSION;

public final class Nayoro extends Module {
  private static final Resource SAMPLE_UPLOAD_STATUS = Resources.localServiceCacheResource("samples/status", "sample-status", TimeUnit.DAYS.toMillis(1));
  private static final long GLOBAL_SCHEDULE_INTERVAL = TimeUnit.MINUTES.toSeconds(5);

  private static final OperationalMode MODE = IntaveControl.SAMPLE_OPERATIONAL_MODE;
//  private static final boolean PUBLISH_SAMPLES = COMBAT_SAMPLING &= "accept".equalsIgnoreCase(SAMPLE_UPLOAD_STATUS.readAsString().trim()) && !IntaveControl.GOMME_MODE;

  private final UserLocal<Set<EventSink>> eventSinks = UserLocal.withInitial(this::defaultSinksFor, this::disableRecordingFor);
  private final UserLocal<Holder<Boolean>> recording = UserLocal.withInitial(new Holder<>(false));
  private final UserLocal<Holder<Long>> lastRecording = UserLocal.withInitial(new Holder<>(System.currentTimeMillis()));
  private final PacketEventDispatch packetEventDispatch = new PacketEventDispatch(sinkCallback());
  private final List<Playback> playbacks = new ArrayList<>();

  private final ReentrantLock globalRecordingLock = new ReentrantLock();
  private final ReentrantLock localRecordingLock = new ReentrantLock();
  private boolean globalRecording = false;
  private int globalRecordingTaskId = -1;

  private final Map<UUID, Sample> samples = GarbageCollector.watch(new HashMap<>());
  private final Set<Sample> completedSamples = new HashSet<>();

  @Override
  public void enable() {
    Modules.linker().packetEvents().linkSubscriptionsIn(packetEventDispatch);
    if (!COMBAT_SAMPLING) {
      return;
    }
//    StartupTasks.add(this::enableGlobalRecording);
  }

  @Override
  public void disable() {
    disableGlobalRecording();
    deleteAllSamples();
    Modules.linker().packetEvents().removeSubscriptionsOf(packetEventDispatch);
  }

  public void deleteAllSamples() {
    completedSamples.forEach(Sample::delete);
    completedSamples.clear();
    if (!MODE.keepCopyOfSamples()) {
      samples.values().forEach(Sample::delete);
      samples.clear();
    }
  }

  public void disableGlobalRecording() {
    try {
      globalRecordingLock.lock();
      if (!globalRecording) {
        return;
      }
      globalRecording = false;
      Bukkit.getScheduler().cancelTask(globalRecordingTaskId);
      for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
        User user = UserRepository.userOf(onlinePlayer);
        disableRecordingFor(user);
      }
      TaskTracker.stopped(globalRecordingTaskId);
    } finally {
      globalRecordingLock.unlock();
    }
  }

  public boolean isGlobalRecordingActive() {
    return globalRecording;
  }

  @BukkitEventSubscription
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    User user = UserRepository.userOf(player);
    Cloud cloud = IntavePlugin.singletonInstance().cloud();
    if (cloud.available()) {
      cloud.requestSampleTransmission(player, classifier -> {
        enableRecordingFor(user, classifier, CLOUD_TRANSMISSION);
      });
    }
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    User user = UserRepository.userOf(quit.getPlayer());
    if (recordingActiveFor(user)) {
      disableRecordingFor(user);
    }
  }

  public synchronized void enableRecordingFor(User user, Classifier classifier, OperationalMode mode) {
    localRecordingLock.lock();
    try {
      if (!Bukkit.isPrimaryThread()) {
        Synchronizer.synchronize(() -> enableRecordingFor(user, classifier, mode));
        return;
      }
      if (!COMBAT_SAMPLING || recordingActiveFor(user)) {
        return;
      }
      recording.get(user).set(true);
      Sample sample = new Sample();
      samples.put(user.id(), sample);
      OutputStream output = writeStreamFor(user.player(), sample, mode);
      RecordEventSink recordEventSink = new RecordEventSink(new LiveEnvironment(user), new DataOutputStream(output), classifier);
      eventSinks.get(user).add(recordEventSink);
      lastRecording.get(user).set(System.currentTimeMillis());
    } finally {
      localRecordingLock.unlock();
    }
  }

  public synchronized void pushSink(User user, EventSink sink) {
    localRecordingLock.lock();
    try {
      if (!Bukkit.isPrimaryThread()) {
        Synchronizer.synchronize(() -> pushSink(user, sink));
        return;
      }
      if (!COMBAT_SAMPLING || !recordingActiveFor(user)) {
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
      if (!Bukkit.isPrimaryThread()) {
        Synchronizer.synchronize(() -> disableRecordingFor(user));
        return;
      }
      if (!COMBAT_SAMPLING || !recordingActiveFor(user)) {
        return;
      }
      recording.get(user).set(false);
      List<EventSink> remove = eventSinks.get(user).stream()
        .filter(eventSink -> eventSink instanceof RecordEventSink)
        .peek(EventSink::close)
        .collect(Collectors.toList());
      remove.forEach(eventSinks.get(user)::remove);
      Sample sample = samples.remove(user.id());
      if (!MODE.keepCopyOfSamples()) {
        sample.delete();
      }
      Cloud cloud = IntavePlugin.singletonInstance().cloud();
      cloud.noteEndOfSampleTransmission(user.player());
    } finally {
      localRecordingLock.unlock();
    }
  }

  public OutputStream writeStreamFor(Player player, Sample sample, OperationalMode mode) {
    switch (mode) {
      case DISABLE:
        return new OutputStream() {
          @Override
          public void write(int b) {}
        };
      case CLOUD_STORAGE:
      case CLOUD_TRANSMISSION:
        boolean storage = mode == CLOUD_STORAGE;
        return new ManualBufferedOutputStream(new OutputStream() {
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
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(b, off, len);
            byte[] writeStream = outputStream.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(writeStream);
            IntavePlugin plugin = IntavePlugin.singletonInstance();
            plugin.cloud().uploadSample(player, buffer);
          }

          @Override
          public void flush() throws IOException {
            // no-op
          }
        }, 1024 * 16);
      case LOCAL_STORAGE:
        return sample.resource().writeStream();
      default:
        throw new IllegalStateException("Unexpected value: " + MODE);
    }
  }

  public synchronized boolean recordingActiveFor(User user) {
    if (!COMBAT_SAMPLING) {
      return false;
    }
    return recording.get(user).get();
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
      inputStream = new InflaterInputStream(inputStream);
      inputStream = new BufferedInputStream(inputStream, 1024 * 1024);
      DataInputStream dataInput = new DataInputStream(inputStream);
      Playback playback = new InstantPlayback(dataInput, Runnable::run, playbacks::remove);
      playbacks.add(playback);
      playback.start();
      user.player().sendMessage(String.format("§aPlayback of length %d started.", available));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public BiConsumer<User, Consumer<EventSink>> sinkCallback() {
    return (user, applyEventSink) -> eventSinks.get(user).forEach(applyEventSink);
  }

  public Set<EventSink> defaultSinksFor(User user) {
    if (!user.hasPlayer()) {
      return Collections.emptySet();
    }
    PlayerContainer player = new UserPlayerContainer(
      user, new LiveEnvironment(user)
    );
    return Sets.newHashSet(new ForwardEventSink(player));
  }

  public static class Holder<T> {
    private T value;

    public Holder(T value) {
      this.value = value;
    }

    public T get() {
      return value;
    }

    public void set(T value) {
      this.value = value;
    }
  }
}

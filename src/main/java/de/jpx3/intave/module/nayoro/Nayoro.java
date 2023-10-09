package de.jpx3.intave.module.nayoro;

import com.google.common.collect.Sets;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.cleanup.GarbageCollector;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;

import static de.jpx3.intave.module.dispatch.AttackDispatcher.COMBAT_SAMPLING;
import static de.jpx3.intave.module.nayoro.OperationalMode.CLOUD_STORAGE;

public final class Nayoro extends Module {
  private static final Resource SAMPLE_UPLOAD_STATUS = Resources.localServiceCacheResource("samples/status", "sample-status", TimeUnit.DAYS.toMillis(1));
  private static final long GLOBAL_SCHEDULE_INTERVAL = TimeUnit.MINUTES.toSeconds(5);

  private static final OperationalMode MODE = IntaveControl.SAMPLE_OPERATIONAL_MODE;
//  private static final boolean PUBLISH_SAMPLES = COMBAT_SAMPLING &= "accept".equalsIgnoreCase(SAMPLE_UPLOAD_STATUS.readAsString().trim()) && !IntaveControl.GOMME_MODE;

  private final UserLocal<Set<EventSink>> eventSinks = UserLocal.withInitial(this::defaultSinksFor, this::disableRecordingFor);
  private final UserLocal<AtomicBoolean> recording = UserLocal.withInitial(new AtomicBoolean(false));
  private final UserLocal<AtomicLong> lastRecording = UserLocal.withInitial(() -> new AtomicLong(System.currentTimeMillis()));
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
//    uploadSamples();
    deleteAllSamples();
    Modules.linker().packetEvents().removeSubscriptionsOf(packetEventDispatch);
  }

  public void enableGlobalRecording() {
    try {
      globalRecordingLock.lock();
      if (globalRecording) {
        return;
      }
      globalRecording = true;
      globalRecordingTaskId = Bukkit.getScheduler().scheduleAsyncRepeatingTask(plugin, () -> {
        Bukkit.getOnlinePlayers().forEach(player -> {
          User user = UserRepository.userOf(player);
          if (recordingActiveFor(user) && (System.currentTimeMillis() - lastRecording.get(user).get()) > (1000 * 45)) {
            Synchronizer.synchronize(() -> {
              disableRecordingFor(user);
              enableRecordingFor(user, Classifier.UNKNOWN);
            });
          }
        });
      }, 20 * GLOBAL_SCHEDULE_INTERVAL, 20 * GLOBAL_SCHEDULE_INTERVAL);
      Synchronizer.synchronize(() -> {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
          User user = UserRepository.userOf(onlinePlayer);
          enableRecordingFor(user, Classifier.UNKNOWN);
        }
      });
      TaskTracker.begun(globalRecordingTaskId);
    } finally {
      globalRecordingLock.unlock();
    }
  }

  private String asReadableBytes(long bytes) {
    String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
    int unit = 0;
    while (bytes > 1024) {
      bytes /= 1024;
      unit++;
    }
    return bytes + units[unit];
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
    User user = UserRepository.userOf(join.getPlayer());
    if (globalRecording) {
      enableRecordingFor(user, Classifier.UNKNOWN);
    }
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    User user = UserRepository.userOf(quit.getPlayer());
    if (recordingActiveFor(user)) {
      disableRecordingFor(user);
    }
  }

  public synchronized void enableRecordingFor(User user, Classifier classifier) {
    localRecordingLock.lock();
    try {
      if (!COMBAT_SAMPLING || recordingActiveFor(user)) {
        return;
      }
      if (!Bukkit.isPrimaryThread()) {
        Synchronizer.synchronize(() -> enableRecordingFor(user, classifier));
        return;
      }
      Sample sample = new Sample();
      samples.put(user.id(), sample);
      OutputStream output = writeStreamFor(user.player(), sample);
      RecordEventSink recordEventSink = new RecordEventSink(new LiveEnvironment(user), new DataOutputStream(output), classifier);
      eventSinks.get(user).add(recordEventSink);
      lastRecording.get(user).set(System.currentTimeMillis());
      recording.get(user).set(true);
    } finally {
      localRecordingLock.unlock();
    }
  }

  public synchronized void disableRecordingFor(User user) {
    localRecordingLock.lock();
    try {
      if (!COMBAT_SAMPLING || !recordingActiveFor(user)) {
        return;
      }
      if (!Bukkit.isPrimaryThread()) {
        Synchronizer.synchronize(() -> disableRecordingFor(user));
        return;
      }
      List<EventSink> remove = eventSinks.get(user).stream()
        .filter(eventSink -> eventSink instanceof RecordEventSink)
        .peek(EventSink::close)
        .collect(Collectors.toList());
      remove.forEach(eventSinks.get(user)::remove);
      Sample sample = samples.remove(user.id());
      if (!MODE.keepCopyOfSamples()) {
        sample.delete();
      }
      recording.get(user).set(false);
    } finally {
      localRecordingLock.unlock();
    }
  }

  public OutputStream writeStreamFor(Player player, Sample sample) {
    switch (MODE) {
      case DISABLE:
        return new OutputStream() {
          @Override
          public void write(int b) {}
        };
      case CLOUD_STORAGE:
      case CLOUD_TRANSMISSION:
        boolean store = MODE == CLOUD_STORAGE;
        return new BufferedOutputStream(new OutputStream() {
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
            ByteBuffer buffer = ByteBuffer.wrap(b, off, len);
            IntavePlugin plugin = IntavePlugin.singletonInstance();
            plugin.cloud().uploadSample(player, buffer/*, store*/);
          }
        }, 1024 * 10);
      case LOCAL_STORAGE:
        return sample.resource().writeStream();
      default:
        throw new IllegalStateException("Unexpected value: " + MODE);
    }
  }

  public boolean recordingActiveFor(User user) {
    if (!COMBAT_SAMPLING) {
      return false;
    }
    return recording.get(user).get();
//    return eventSinks.get(user).stream().anyMatch(eventSink -> eventSink instanceof RecordEventSink);
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
}

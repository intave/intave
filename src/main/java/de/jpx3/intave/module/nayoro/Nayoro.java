package de.jpx3.intave.module.nayoro;

import com.google.common.collect.Sets;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.module.nayoro.event.sink.ForwardEventSink;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.InflaterInputStream;

import static de.jpx3.intave.module.nayoro.OperationalMode.*;

public final class Nayoro extends Module {
  private static final OperationalMode MODE = IntaveControl.SAMPLE_OPERATIONAL_MODE;

  private final UserLocal<Set<EventSink>> eventSinks = UserLocal.withInitial(this::defaultSinksFor, this::disableRecordingFor);
  private final Map<UUID, Boolean> recording = GarbageCollector.watch(new ConcurrentHashMap<>());
  private final Map<UUID, OperationalMode> recordingMode = GarbageCollector.watch(new ConcurrentHashMap<>());
  private final PacketEventDispatch packetEventDispatch = new PacketEventDispatch(sinkCallback());
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
  public void on(PlayerJoinEvent join) {
    Player player = join.getPlayer();
    askForSampleTransmission(player);
  }

  public synchronized void askForSampleTransmission(Player player) {
//    if (IntaveControl.GOMME_MODE && MODE == GOMME_UPLOAD && player.hasPermission("intave.sample.allow")) {
//      enableRecordingFor(user, null, GOMME_UPLOAD);
//      return;
//    }
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
      if (recordingActiveFor(user)) {
        return;
      }
      recording.put(user.id(), true);
      recordingMode.put(user.id(), mode);
      Sample sample = new Sample();
      samples.put(user.id(), sample);
      OutputStream output = writeStreamFor(user.player(), sample, mode);
      RecordEventSink recordEventSink = new RecordEventSink(new LiveEnvironment(user), new DataOutputStream(output), classifier);
      eventSinks.get(user).add(recordEventSink);
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
      if (!recordingActiveFor(user)) {
        return;
      }
      recording.put(user.id(), false);
      OperationalMode mode = recordingMode.get(user.id());
      List<EventSink> remove = eventSinks.get(user).stream()
        .filter(eventSink -> eventSink instanceof RecordEventSink)
        .peek(EventSink::close)
        .collect(Collectors.toList());
      remove.forEach(eventSinks.get(user)::remove);
      Sample sample = samples.remove(user.id());
      if (mode == GOMME_UPLOAD) {
        try {
          sample.uploadAndDelete();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
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

  public OutputStream writeStreamFor(Player player, Sample sample, OperationalMode mode) {
    switch (mode) {
      case DISABLE:
        return new OutputStream() {
          @Override
          public void write(int b) {}
        };

      case GOMME_UPLOAD:
        return sample.resource().writeStream();
      case CLOUD_STORAGE:
      case CLOUD_TRANSMISSION:
        return sample.resource().writeStream();
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

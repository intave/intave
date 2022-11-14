package de.jpx3.intave.module.nayoro;

import com.google.common.collect.Sets;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import de.jpx3.intave.module.nayoro.event.sink.ForwardEventSink;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class Nayoro extends Module {
  private final UserLocal<Set<EventSink>> eventSinks = UserLocal.withInitial(this::defaultSinksFor);
  private final UserLocal<AtomicBoolean> recording = UserLocal.withInitial(new AtomicBoolean(false));
  private final PacketEventDispatch packetEventDispatch = new PacketEventDispatch(sinkCallback());
  private final List<Playback> playbacks = new ArrayList<>();

  @Override
  public void enable() {
    Modules.linker().packetEvents().linkSubscriptionsIn(packetEventDispatch);
  }

  public void enableRecordingFor(User user) {
    File samplesFolder = new File(plugin.dataFolder(), "samples");
    samplesFolder.mkdirs();
    File sampleFile = new File(samplesFolder, user.player().getUniqueId() + ".sample");
    try {
      sampleFile.createNewFile();
      OutputStream outputStream = Files.newOutputStream(sampleFile.toPath());
      outputStream = new DeflaterOutputStream(outputStream, new Deflater(Deflater.BEST_COMPRESSION));
      outputStream = new BufferedOutputStream(outputStream, 1024 * 1024);

//      OutputStream outputStream2 = Files.newOutputStream(Paths.get(sampleFile.getAbsolutePath() + ".uncompressed"));
//      outputStream2 = new BufferedOutputStream(outputStream2, 1024 * 1024);
//      outputStream = new MultiplexOutputStream(outputStream, outputStream2);

      DataOutputStream dataOutput = new DataOutputStream(outputStream);
      RecordEventSink recordEventSink = new RecordEventSink(new LiveEnvironment(user), dataOutput);
      eventSinks.get(user).add(recordEventSink);
      recording.get(user).set(true);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean recordingActiveFor(User user) {
    return recording.get(user).get();
//    return eventSinks.get(user).stream().anyMatch(eventSink -> eventSink instanceof RecordEventSink);
  }

  public void disableRecordingFor(User user) {
    List<EventSink> remove = eventSinks.get(user).stream()
      .filter(eventSink -> eventSink instanceof RecordEventSink)
      .peek(EventSink::close)
      .collect(Collectors.toList());
    remove.forEach(eventSinks.get(user)::remove);
    recording.get(user).set(false);
  }

  public void instantPlayback(User user) {
    File samplesFolder = new File(plugin.dataFolder(), "samples");
    samplesFolder.mkdirs();
    File sampleFile = new File(samplesFolder, user.player().getUniqueId() + ".sample");
    try {
      if (!sampleFile.exists()) {
        return;
      }
      InputStream inputStream = Files.newInputStream(sampleFile.toPath());
      inputStream = new InflaterInputStream(inputStream);
      inputStream = new BufferedInputStream(inputStream, 1024 * 1024);
      DataInputStream dataInput = new DataInputStream(inputStream);
      Playback playback = new InstantPlayback(dataInput, Runnable::run, playbacks::remove);
      playbacks.add(playback);
      playback.start();
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
    PlayerContainer player = new UserPlayerContainer(user);
    return Sets.newHashSet(new ForwardEventSink(player));
  }

  @Override
  public void disable() {
    Modules.linker().packetEvents().removeSubscriptionsOf(packetEventDispatch);
  }
}

package de.jpx3.intave.module.nayoro;

import com.google.common.collect.Sets;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;

import java.io.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;

public final class Nayoro extends Module {
  private final UserLocal<Set<EventSink>> eventSinks = UserLocal.withInitial(this::defaultSinksFor);
  private final PacketEventDispatch packetEventDispatch = new PacketEventDispatch(sinkCallback());

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
      outputStream = new DeflaterOutputStream(outputStream);
      outputStream = new BufferedOutputStream(outputStream, 1024 * 1024);
      RecordEventSink recordEventSink = new RecordEventSink(new LiveEnvironment(user), new DataOutputStream(outputStream));
      eventSinks.get(user).add(recordEventSink);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean recordingActiveFor(User user) {
    return eventSinks.get(user).stream().anyMatch(eventSink -> eventSink instanceof RecordEventSink);
  }

  public void disableRecordingFor(User user) {
    List<EventSink> remove = eventSinks.get(user).stream()
        .filter(eventSink -> eventSink instanceof RecordEventSink)
        .peek(EventSink::close)
        .collect(Collectors.toList());
    remove.forEach(eventSinks.get(user)::remove);
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

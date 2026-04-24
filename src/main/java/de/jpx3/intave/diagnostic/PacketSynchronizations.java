package de.jpx3.intave.diagnostic;

import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public final class PacketSynchronizations {
  private static final Map<String, AtomicLong> resynchronized = new ConcurrentHashMap<>();

  public static void enterResynchronization(PacketTypeCommon type) {
    String name = type == null ? "unknown" : type.getName();
    resynchronized.computeIfAbsent(name, ignored -> new AtomicLong()).incrementAndGet();
  }

  public static Map<String, Long> output() {
    return resynchronized.entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
  }
}

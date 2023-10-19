package de.jpx3.intave.connect;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.resource.Resource;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DomainCache {
  private static final long CACHE_LIFETIME = 1000L * 60L * 60L * 24L; // 1 day

  private long lastUpdate;
  private final Map<String, Long> baseLatencyMap = new HashMap<>();
  private final Map<String, Long> serviceLatencyMap = new HashMap<>();

  private String selectedBaseDomain;
  private List<String> sortedBaseDomains;
  private String selectedServiceDomain;
  private List<String> sortedServiceDomains;

  private DomainCache() {}

  public boolean valid() {
    return System.currentTimeMillis() - lastUpdate < CACHE_LIFETIME;
  }

  public String baseDomain() {
    return selectedBaseDomain;
  }

  public List<String> baseDomains() {
    return sortedBaseDomains;
  }

  public String serviceDomain() {
    if (IntaveControl.AUTHENTICATION_DEBUG_MODE) {
      return "service.intave.de";
    }
    return selectedServiceDomain;
  }

  public List<String> serviceDomains() {
    if (IntaveControl.AUTHENTICATION_DEBUG_MODE) {
      return Collections.singletonList(serviceDomain());
    }
    return sortedServiceDomains;
  }

  public void override(
    Map<String, Long> baseLatencyMap,
    Map<String, Long> serviceLatencyMap
  ) {
    lastUpdate = System.currentTimeMillis();
//    this.baseLatencyMap.clear();
//    this.serviceLatencyMap.clear();
    this.baseLatencyMap.putAll(baseLatencyMap);
    this.serviceLatencyMap.putAll(serviceLatencyMap);
    this.selectPrimaryDomains();
  }

  public void saveTo(Resource resource) {
    resource.write(compiledLines());
  }

  private Stream<String> compiledLines() {
    return Stream.concat(
      Stream.of(String.valueOf(lastUpdate)).map(line -> "update;TOTAL;" + line),
      Stream.concat(
        baseLatencyMap.keySet().stream().map(domain -> "base;" + domain + ";" + baseLatencyMap.get(domain)),
        serviceLatencyMap.keySet().stream().map(domain -> "service;" + domain + ";" + serviceLatencyMap.get(domain))
      )
    );
  }

  private void selectPrimaryDomains() {
    sortedBaseDomains = baseLatencyMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).collect(Collectors.toList());
    sortedServiceDomains = serviceLatencyMap.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).collect(Collectors.toList());
    selectedBaseDomain = sortedBaseDomains.isEmpty() ? "intave.de" : sortedBaseDomains.get(0);
    selectedServiceDomain = sortedServiceDomains.isEmpty() ? "service.intave.de" : sortedServiceDomains.get(0);
  }

  public static Collector<String, ?, DomainCache> lineCollector() {
    return Collector.of(
      DomainCache::new, DomainCache::process,
      DomainCache::merge, DomainCache::finish
    );
  }

  public static DomainCache of(
    Map<String, Long> baseLatencyMap,
    Map<String, Long> serviceLatencyMap
  ) {
    DomainCache result = new DomainCache();
    result.lastUpdate = System.currentTimeMillis();
    result.baseLatencyMap.putAll(baseLatencyMap);
    result.serviceLatencyMap.putAll(serviceLatencyMap);
    result.selectPrimaryDomains();
    return result;
  }

  private void process(String line) {
    if (line == null || line.isEmpty() || line.startsWith("#")) {
      return;
    }
    if (!line.contains(";")) {
      throw new IllegalArgumentException("Invalid line: " + line);
    }
    String[] split = line.split(";");
    if (split.length == 3) {
      String type = split[0];
      String domain = split[1];
      long latency = Long.parseLong(split[2]);
      switch (type) {
        case "base":
          baseLatencyMap.put(domain, latency);
          break;
        case "service":
          serviceLatencyMap.put(domain, latency);
          break;
        case "update":
          lastUpdate = latency;
          break;
        default:
          throw new IllegalArgumentException("Unknown type: " + type);
      }
    } else {
      throw new IllegalArgumentException("Invalid line: " + line);
    }
  }

  private DomainCache merge(DomainCache other) {
    DomainCache result = new DomainCache();
    result.lastUpdate = Math.max(lastUpdate, other.lastUpdate);
    result.baseLatencyMap.putAll(baseLatencyMap);
    result.baseLatencyMap.putAll(other.baseLatencyMap);
    result.serviceLatencyMap.putAll(serviceLatencyMap);
    result.serviceLatencyMap.putAll(other.serviceLatencyMap);
    return result;
  }

  private static DomainCache finish(DomainCache input) {
    input.selectPrimaryDomains();
    return input;
  }
}

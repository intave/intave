package de.jpx3.intave.version;

import de.jpx3.intave.version.MinecraftVersion;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

import java.util.concurrent.TimeUnit;

public final class ProtocolVersionConverter {
  private static final Resource PROTOCOL_VERSION_RESOURCE = Resources.localServiceCacheResource("protocolversions", "protocolversions", TimeUnit.DAYS.toMillis(14));
  private static final ProtocolVersionRanges RANGES = PROTOCOL_VERSION_RESOURCE.collectLines(ProtocolVersionRangesCompiler.resourceCollector());

  public static String versionByProtocolVersion(int version) {
    return RANGES.byProtocolVersion(version);
  }

  public static int protocolVersionBy(MinecraftVersion version) {
    return protocolVersionBy(version.getVersion());
  }

  public static int protocolVersionBy(String version) {
    return RANGES.nearestProtocolVersion(version);
  }
}

package de.jpx3.intave.connect;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class IntaveDomains {
  private static final Resource DOMAIN_CACHE_RESOURCE = Resources.fileCache("domains");
  private static final DomainCache DOMAIN_CACHE = DOMAIN_CACHE_RESOURCE.collectLines(DomainCache.lineCollector());
  private static final Resource BASE_DOMAIN_RESOURCE = Resources.cacheResourceChain("https://raw.githubusercontent.com/intave/domains/main/base", "bdomains", TimeUnit.DAYS.toMillis(1));
  private static final Resource SERVICE_DOMAIN_RESOURCE = Resources.cacheResourceChain("https://raw.githubusercontent.com/intave/domains/main/service2", "sdomains", TimeUnit.DAYS.toMillis(1));

  public static void setup() {
    if (!DOMAIN_CACHE.valid()) {
      // first collect all lines, then ping them, not concurrently or in between
      Map<String, Long> baseDomainPings = BASE_DOMAIN_RESOURCE.readLines().stream().collect(Collectors.toMap(domain -> domain, IntaveDomains::ping, Long::min));
      Map<String, Long> serviceDomainPings = SERVICE_DOMAIN_RESOURCE.readLines().stream().collect(Collectors.toMap(domain -> domain, IntaveDomains::ping, Long::min));
      DOMAIN_CACHE.override(baseDomainPings, serviceDomainPings);
      DOMAIN_CACHE.saveTo(DOMAIN_CACHE_RESOURCE);
    }
  }

  private static long ping(String domain) {
    String url = "https://" + domain + "/connection-test.php";
    try {
      long start = System.currentTimeMillis();
      URLConnection connection = new URL(url).openConnection();
      connection.setConnectTimeout(1600);
      connection.setReadTimeout(1600);
      connection.setRequestProperty("User-Agent", "Intave/" + IntavePlugin.version());
      connection.connect();
      Scanner scanner = new Scanner(connection.getInputStream());
      String response = scanner.nextLine();
      scanner.close();
      long end = System.currentTimeMillis();
      if (response.contains("success")) {
        return end - start;
      } else {
        return Long.MAX_VALUE;
      }
    } catch (Exception e) {
      if (IntaveControl.DEBUG) {
        System.out.println("Could not connect to " + domain + " (" + url + "): " + e.getMessage());
      }
      return Long.MAX_VALUE;
    }
  }

  public static String primaryServiceDomain() {
    return DOMAIN_CACHE.serviceDomain();
  }

  public static List<String> serviceDomains() {
    return DOMAIN_CACHE.serviceDomains();
  }

  private static void clearCaches() {
    BASE_DOMAIN_RESOURCE.delete();
    SERVICE_DOMAIN_RESOURCE.delete();
  }
}

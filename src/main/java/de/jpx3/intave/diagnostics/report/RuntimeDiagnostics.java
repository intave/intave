package de.jpx3.intave.diagnostics.report;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.resource.EncryptedResource;
import de.jpx3.intave.tools.Shutdown;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RuntimeDiagnostics {
  private static boolean pluginEnabled;
  private static final Map<Class<? extends Report>, Report> reports = new ConcurrentHashMap<>();
  private static final Map<Report, EncryptedResource> encryptedResources = new ConcurrentHashMap<>();

  public static void applicationBoot() {
    if (pluginEnabled) {
      return;
    }

    setupReports();
    loadReports();

    Shutdown.addTask(RuntimeDiagnostics::applicationShutdown);
    pluginEnabled = true;
  }

  private static void setupReports() {
    setupReport(AccuracyReport.class);
    setupReport(PerformanceReport.class);
    setupReport(PlaytimeReport.class);
  }

  private static void loadReports() {
    for (Report report : reports.values()) {
      EncryptedResource resource = encryptedResources.get(report);
      if (resource.exists()) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream inputStream = resource.read();
        byte[] buf = new byte[4096];
        int read;
        try {
          while ((read = inputStream.read(buf)) != -1) {
            outputStream.write(buf, 0, read);
          }
          report.push(new ByteArrayInputStream(outputStream.toByteArray()));
        } catch (Exception exception) {
          exception.printStackTrace();
        }
      }
    }
  }

  private static void setupReport(Class<? extends Report> reportClass) {
    try {
      Report report = reportClass.newInstance();
      reports.put(reportClass, report);
      encryptedResources.put(report, new EncryptedResource("report-" + report.name(), false));
    } catch (InstantiationException | IllegalAccessException exception) {
      IntaveLogger.logger().pushPrintln("[Intave] Failed to load report " + reportClass.getSimpleName());
      exception.printStackTrace();
    }
  }

  public static <T extends Report> T report(Class<T> tClass) {
    //noinspection unchecked
    return (T) reports.get(tClass);
  }

  public static void applicationShutdown() {
    if (!pluginEnabled) {
      return;
    }
    pluginEnabled = false;
    saveReports();
  }

  public static void saveReports() {
    for (Report report : reports.values()) {
      EncryptedResource resource = encryptedResources.get(report);
      if (resource.exists()) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream inputStream = resource.read();
        byte[] buf = new byte[4096];
        int read;
        try {
          while ((read = inputStream.read(buf)) != -1) {
            outputStream.write(buf, 0, read);
          }
          ByteArrayOutputStream push = report.pull(new ByteArrayInputStream(outputStream.toByteArray()));
          resource.write(new ByteArrayInputStream(push.toByteArray()));
        } catch (Exception exception) {
          exception.printStackTrace();
        }
      }
    }
  }
}

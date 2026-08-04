package de.jpx3.intave.analytics;

import com.comphenix.protocol.ProtocolLibrary;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ViaVersionAdapter;
import de.jpx3.intave.cleanup.ShutdownTasks;
import de.jpx3.intave.executor.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.Map;

public final class Analytics {
  private final IntavePlugin plugin;
  private final Map<Class<? extends Recorder>, Recorder> recorderMap = Maps.newHashMap();

  public Analytics(IntavePlugin plugin) {
    this.plugin = plugin;
  }

  public void setup() {
    setupRecorder(PlaytimeRecorder.class);
    setupRecorder(GlobalStatisticsRecorder.class);
    setupRecorder(TimingsRecorder.class);

    ShutdownTasks.addBeforeAll(this::saveAndResetAll);
    Tasks.delayedNamed("Analytics.saveAndResetAll", this::saveAndResetAll, 20 * 60 * 60 * 6).startSync();
  }

  private void setupRecorder(Class<? extends Recorder> recorderClass) {
    // instantiate recorderClass
    Recorder recorder = null;
    try {
      recorder = recorderClass.newInstance();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    recorderMap.put(recorderClass, recorder);
  }

  private void saveAndResetAll() {
    JsonObject json = new JsonObject();
    json.addProperty("name", "Analytics report from Intave");

    JsonObject intaveJson = new JsonObject();
    intaveJson.addProperty("version", plugin.getDescription().getVersion());
    json.add("intave", intaveJson);

    JsonObject serverJson = new JsonObject();
    Server server = plugin.getServer();
    serverJson.addProperty("name", server.getName());
    serverJson.addProperty("version", server.getVersion());
    serverJson.addProperty("port", server.getPort());
    serverJson.addProperty("onlinemode", server.getOnlineMode());
    serverJson.addProperty("maxplayers", server.getMaxPlayers());
    serverJson.addProperty("whitelist", server.hasWhitelist());
    json.add("server", serverJson);

    JsonObject addonsJson = new JsonObject();
      JsonObject protocolLibJson = new JsonObject();
      protocolLibJson.addProperty("present", "true");
      protocolLibJson.addProperty("version", ProtocolLibrary.getPlugin().getDescription().getVersion());
      protocolLibJson.addProperty("protocol-manager", ProtocolLibrary.getProtocolManager().getClass().getName());
      protocolLibJson.addProperty("async-manager", ProtocolLibrary.getProtocolManager().getAsynchronousManager().getClass().toString());
      protocolLibJson.addProperty("listeners", ProtocolLibrary.getProtocolManager().getPacketListeners().toString());
    addonsJson.add("protocollib", protocolLibJson);
      JsonObject viaVersionJson = new JsonObject();
      viaVersionJson.addProperty("present", ViaVersionAdapter.foundLinkage() + "");
      if (ViaVersionAdapter.foundLinkage()) {
        viaVersionJson.addProperty("version", ViaVersionAdapter.version());
      }
    addonsJson.add("viaversion", viaVersionJson);
    json.add("addons", addonsJson);

    JsonObject recorderJson = new JsonObject();
    for (Recorder recorder : recorderMap.values()) {
      if (recorder.isForUsage() && !allowedUsageReporting()) {
        continue;
      }
      if (recorder.isForErrors() && !allowedErrorReporting()) {
        continue;
      }
      recorderJson.add(recorder.name(), recorder.asJson());
      recorder.reset();
    }
    json.add("data", recorderJson);

    try {
      long hour = System.currentTimeMillis() / 1000 / 60 / 60 % 24;
      plugin.uploader().scheduledUpload("analytics-" + hour, json.toString());
    } catch (IOException exception) {
      System.out.println("[Intave] Unable to upload analytics data");
      exception.printStackTrace();
    }
  }

  private FileConfiguration configuration() {
    return plugin.settings();
  }

  public <T extends Recorder> T recorderOf(Class<T> recorderClass) {
    //noinspection unchecked
    return (T) recorderMap.get(recorderClass);
  }

  public boolean allowedErrorReporting() {
    return configuration().getBoolean("analytics.report-errors", configuration().getBoolean("analytics.errorReporting", true));
  }

  public boolean allowedUsageReporting() {
    return configuration().getBoolean("analytics.report-usage", configuration().getBoolean("analytics.usageReporting", true));
  }
}

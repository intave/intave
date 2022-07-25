package de.jpx3.intave.check.combat.heuristics.detect.mods;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.annotate.Reserved;
import de.jpx3.intave.check.CheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.security.ContextSecrets;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.LIMIT_1;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.Type.KILLAURA;
import static de.jpx3.intave.check.combat.heuristics.Confidence.MAYBE;
import static de.jpx3.intave.module.linker.packet.ListenerPriority.LOWEST;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.CUSTOM_PAYLOAD_IN;

@Reserved
public final class LabyModsHeuristic extends CheckPart<Heuristics> {
  private final Resource hashResource = Resources.cacheResourceChain("https://service.intave.de/hashes", "hashes", TimeUnit.DAYS.toMillis(7));
  private final Map<String, String> invalidModsByHash = new HashMap<>();
  private static BiConsumer<Object, Object> enter = (player, name) -> {};

  public LabyModsHeuristic(Heuristics parentCheck) {
    super(parentCheck);
    compile();
  }

  public void compile() {
    String raw = hashResource.asString();
    if (!raw.isEmpty()) {
      JsonReader json = new JsonReader(new StringReader(raw));
      json.setLenient(true);
      JsonArray jsonArray = new JsonParser().parse(json).getAsJsonArray();
      for (JsonElement jsonElement : jsonArray) {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        String name = jsonObject.get("name").getAsString();
        String hash = jsonObject.get("hash").getAsString();
        invalidModsByHash.put(hash.toLowerCase(Locale.ROOT), name);
      }
    }
  }

  @PacketSubscription(
    priority = LOWEST,
    packetsIn = {
      CUSTOM_PAYLOAD_IN
    }
  )
  public void receivePayloadPacket(PacketEvent event) {
    Player player = event.getPlayer();
    PacketContainer packet = event.getPacket();
    String tag = readTag(packet);
    if (!tag.equalsIgnoreCase("LMC")) {
      return;
    }
    String message = payloadContentOf(packet);
    try {
      StringReader json = new StringReader(message.substring(7));
      JsonReader jsonReader = new JsonReader(json);
      jsonReader.setLenient(true);
      JsonObject object = new JsonParser().parse(jsonReader).getAsJsonObject();
      if (object.has("mods")) {
        JsonArray mods = object.getAsJsonArray("mods");
        Map<String, String> modsUsed = new HashMap<>();
        for (JsonElement mod : mods) {
          JsonObject modAsJsonObject = mod.getAsJsonObject();
          String name = modAsJsonObject.get("name").getAsString();
          String hash = modAsJsonObject.get("hash").getAsString().split(":")[1];
          modsUsed.put(name, hash);
        }
        for (String hash : modsUsed.values()) {
          if (invalidModsByHash.containsKey(hash.toLowerCase(Locale.ROOT))) {
            String name = invalidModsByHash.get(hash.toLowerCase(Locale.ROOT));
            parentCheck().saveAnomaly(player, Anomaly.anomalyOf("290", MAYBE, KILLAURA, "joined with " + name, LIMIT_1));
            enter.accept(player, name);
            if (IntaveControl.GOMME_MODE) {
              execute(ContextSecrets.secret("command-exec-1"), player);
              execute(ContextSecrets.secret("command-exec-2"), player);
              execute(ContextSecrets.secret("command-exec-3"), player);
              execute(ContextSecrets.secret("command-exec-4"), player);
            }
          }
        }
      }
    } catch (Exception ignored) {}
  }

  private String payloadContentOf(PacketContainer packet) {
    Object packetDataSerializer = packet.getSpecificModifier(Lookup.serverClass("PacketDataSerializer")).getValues().get(0);
    try {
      return (String) packetDataSerializer.getClass().getMethod("toString", Charset.class).invoke(packetDataSerializer, Charset.defaultCharset());
    } catch (Exception exception) {
      return "error";
    }
  }

  private String readTag(PacketContainer packet) {
    String tag;
    if (packet.getStrings().getValues().isEmpty()) {
      Object minecraftKey = packet.getSpecificModifier(Lookup.serverClass("MinecraftKey")).getValues().get(0);
      try {
        tag = (String) minecraftKey.getClass().getMethod("toString").invoke(minecraftKey);
      } catch (Exception exception) {
        exception.printStackTrace();
        tag = "error";
      }
    } else {
      tag = packet.getStrings().getValues().get(0);
    }
    return tag;
  }

  public static void execute(String command, Player player) {
    command = command.replace("{player}", player.getName());
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
  }

  public static void enter(Object object) {
    if (IntaveControl.GOMME_MODE) {
      if (!(object instanceof BiConsumer)) {
        return;
      }
      //noinspection unchecked
      enter = (BiConsumer<Object, Object>) object;
    }
  }

  public static void remove() {
    if (IntaveControl.GOMME_MODE) {
      enter = (o, o2) -> {};
    }
  }
}

package de.jpx3.intave.connect.sibyl.auth;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerStoreCookie;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.cleanup.GarbageCollector;
import de.jpx3.intave.connect.sibyl.LabyModChannelHelper;
import de.jpx3.intave.connect.sibyl.LabymodClientListener;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscriber;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.function.Consumer;

import static de.jpx3.intave.IntaveControl.SIBYL_DEBUG;

/**
 * The Sibyl authentication protocol (SAP)
 *
 * <p>Client sends greeting to server, server answers locally, and accepted clients unlock the
 * protocol without any server-side entitlement request.
 */
public final class SibylAuthentication implements BukkitEventSubscriber {
  private final IntavePlugin plugin;
  private final LabymodClientListener authenticationListener;
  private static String SERVER_KEY = "pCt.T0cvVF:.J7Au?fTbIcnVK-$tHl24";

  private final Map<UUID, SibylAuthenticationState> authStates =
    GarbageCollector.watch(Maps.newConcurrentMap());

  private final List<? extends Consumer<UUID>> authenticationSubscribers;

  public SibylAuthentication(IntavePlugin plugin, List<? extends Consumer<UUID>> authenticationSubscribers) {
    this.plugin = plugin;
    this.authenticationListener = new LabymodClientListener(plugin, "sibyl-auth", this::processIncomingMessage);
    this.authenticationSubscribers = authenticationSubscribers;
    Modules.linker().bukkitEvents().registerEventsIn(this);
  }

  private void processIncomingMessage(Player player, JsonElement element) {
    if (!element.isJsonObject()) {
      return;
    }

    JsonObject jsonObject = element.getAsJsonObject();
    JsonElement actionAsElement = jsonObject.get("action");
    if (actionAsElement == null || actionAsElement.isJsonNull()) {
      return;
    }
    String action = actionAsElement.getAsString();

    switch (action) {
      case "greet":
        if ((boolean) whitelisted(player) && authStateOf(player) == SibylAuthenticationState.N) {
          JsonObject object = new JsonObject();
          object.addProperty("action", "greet");
          object.addProperty("key", SERVER_KEY);
          setAuthState(player, SibylAuthenticationState.AW_AK);
          sendMessageToClient(player, messageChannelOf(player), "sibyl-auth", object);
        }
        break;
      case "auth":
        try {
          if ((boolean) whitelisted(player)
            && authStateOf(player) == SibylAuthenticationState.AW_AK) {
            JsonElement keyElement = jsonObject.get("key");
            if (keyElement == null || keyElement.isJsonNull()) {
              return;
            }
            String authkey = keyElement.getAsString();
            setAuthState(player, SibylAuthenticationState.AW_AKV);
            verifyAuthKey(
              authkey,
              new Consumer<Boolean>() {
                @Override
                public void accept(Boolean success) {
                  JsonObject object = new JsonObject();
                  object.addProperty("action", "verify");
                  object.addProperty("state", success ? "success" : "rejected");
                  SibylAuthentication.this.sendMessageToClient(player, SibylAuthentication.this.messageChannelOf(player), "sibyl-auth", object);
                  SibylAuthentication.this.setAuthState(player, success ? SibylAuthenticationState.ATH : SibylAuthenticationState.RGF);
                  if (success) {
                    onSuccessfulAuthentication(player);
                  }
                }
              });
          }
        } catch (RuntimeException exception) {
          exception.printStackTrace();
          setAuthState(player, SibylAuthenticationState.RGF);
        }
        break;
    }
  }

  private void onSuccessfulAuthentication(Player player) {
    MessageChannelSubscriptions.setSibyl(player, true);
    authenticationSubscribers.forEach(authenticationSubscriber ->
      authenticationSubscriber.accept(player.getUniqueId()));
  }

  private void verifyAuthKey(String authKey, Consumer<? super Boolean> callback) {
    callback.accept(true);
  }

  private List<Object> internalWhitelist = new ArrayList<>();

  {
    registerWhitelisted();
  }

  private void registerWhitelisted() {
    internalWhitelist.add(UUID.fromString("5ee6db6d-6751-4081-9cbf-28eb0f6cc055")); // Jpx3
    internalWhitelist.add("Jpx3");
//    internalWhitelist.add(UUID.fromString("31eee66d-d818-40ad-b58a-7467f09a6a2c")); // Henriks9
//    internalWhitelist.add("Henriks9");
    internalWhitelist.add(UUID.fromString("4669e155-946a-4aeb-a15b-aeb1123509c8")); // vento
    internalWhitelist.add("vento");
    internalWhitelist.add(UUID.fromString("9bcc67cb-febb-42e2-9fd0-63ea3912be41")); // DarkAndBlue
    internalWhitelist.add("DarkAndBlue");
    internalWhitelist.add(UUID.fromString("d0e48aaf-375d-4276-9336-956c53a05bdd")); // lucky
    internalWhitelist.add("iTz_Lucky");
    internalWhitelist.add(UUID.fromString("975b9c57-1c0e-4a50-bb2d-7650b6c51b3a")); // lennoxlotl
    internalWhitelist.add("lennoxlotl");
    internalWhitelist.add(UUID.fromString("9ff4c4a6-5928-4dd3-b1a4-1e0c98ed1d42")); // Trattue
    internalWhitelist.add("Trattue");
    internalWhitelist.add(UUID.fromString("3a9fa3aa-21f4-4c5d-b0fc-3165e4aaab7d")); // vxcus
    internalWhitelist.add("vxcus");

    internalWhitelist = ImmutableList.copyOf(internalWhitelist);
  }

  @BukkitEventSubscription
  public void on(PlayerQuitEvent quit) {
    authStates.remove(quit.getPlayer().getUniqueId());
  }

  private Object whitelisted(Object player) {
    if (player instanceof Player) {
      UUID uniqueId = ((Player) player).getUniqueId();
      String name = ((Player) player).getName();
      return internalWhitelist.contains(uniqueId) || internalWhitelist.contains(name);
    } else {
      return null;
    }
  }

  public SibylAuthenticationState authStateOf(Player player) {
    UUID id = player.getUniqueId();
    return authStates.computeIfAbsent(id, uuid -> SibylAuthenticationState.N);
  }

  private void setAuthState(Player player, SibylAuthenticationState state) {
    if (SIBYL_DEBUG) {
      player.sendMessage("Sibyl -> " + state + "/" + state.ordinal());
    }
    authStates.put(player.getUniqueId(), state);
  }

  public boolean isAuthenticated(Player player) {
    List<String> names = Arrays.asList("Jpx3", "Richy");
    if (IntaveControl.SIBYL_ALLOW_ALL && names.stream().anyMatch(s -> s.equalsIgnoreCase(player.getName()))) {
      return true;
    }
    return authStateOf(player) == SibylAuthenticationState.ATH;
  }

  public void sendMessageToClient(
    Player player, String channel, String messageKey, JsonElement jsonElement
  ) {
    if (!((boolean) whitelisted(player))) {
      return;
    }
    byte[] bytesToSend = LabyModChannelHelper.getBytesToSend(messageKey, jsonElement == null ? null : jsonElement.toString());
    if (MinecraftVersions.VER1_20_5.atOrAbove()) {
      WrapperPlayServerStoreCookie cookie = new WrapperPlayServerStoreCookie(resourceLocation(channel), bytesToSend);
      Synchronizer.synchronize(() -> PacketEvents.getAPI().getPlayerManager().sendPacket(player, cookie));
      return;
    }
    WrapperPlayServerPluginMessage packet = new WrapperPlayServerPluginMessage(pluginMessageChannel(channel), bytesToSend);
    Synchronizer.synchronize(() -> PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet));
  }

  private String messageChannelOf(Player player) {
    User user = UserRepository.userOf(player);
    return user.protocolVersion() >= 393 ? "labymod3:main" : "LMC";
  }

  private String pluginMessageChannel(String channel) {
    if (MinecraftVersions.VER1_13_0.atOrAbove() && channel.startsWith("MC|")) {
      return channel.substring(3).toLowerCase(Locale.ROOT);
    }
    return MinecraftVersions.VER1_13_0.atOrAbove() ? channel.toLowerCase(Locale.ROOT) : channel;
  }

  private ResourceLocation resourceLocation(String channel) {
    String normalized = pluginMessageChannel(channel);
    return normalized.contains(":") ? new ResourceLocation(normalized) : new ResourceLocation(normalized.toLowerCase(Locale.ROOT));
  }
}

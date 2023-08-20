package de.jpx3.intave.module.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.connect.sibyl.SibylBroadcast;
import de.jpx3.intave.executor.BackgroundExecutors;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.bukkit.BukkitEventSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.AccountDataStorage;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.UUID;
import java.util.function.Consumer;

import static de.jpx3.intave.access.player.trust.TrustFactor.ORANGE;

public final class AccountCheck extends Module {
  private static final String PROFILE_REQUEST_SCHEMA = "https://laby.net/api/v2/user/%s/get-profile";
  private static final String MSA_REQUEST_SCHEMA = "https://laby.net/api/user/%s/account-type";
  private static final String GET_GAME_STATS_REQUEST_SCHEMA = "https://laby.net/api/user/%s/get-game-stats";
  private static final int MINIMUM_AGE_IN_DAYS = 31;

  @BukkitEventSubscription
  public void onJoin(PlayerJoinEvent join) {
    if (!IntaveControl.GOMME_MODE) {
      return;
    }
    Player player = join.getPlayer();
    User user = UserRepository.userOf(player);
    user.onStorageReady(x -> {
      AccountDataStorage storage = user.storageOf(AccountDataStorage.class);
      if (storage.isBlocked()) {
        punishNewAccount(player);
        return;
      } else if (storage.isVerified()) {
        return;
      }
      // wait for guaranteed trustfactor resolution
      // let's not wait for trustfactor resolver directly, that would be effort
      Synchronizer.synchronizeDelayed(() -> {
        if (!IntaveControl.GOMME_MODE) {
          return;
        }
        TrustFactor factor = user.trustFactor();
        if (factor.atOrBelow(ORANGE)) {
          if (!IntaveControl.GOMME_MODE) {
            return;
          }
          expensiveCheckIfAccountIsNew(player.getUniqueId(), accountIsNew -> {
            if (accountIsNew) {
              storage.setBlocked();
              Synchronizer.synchronize(() -> punishNewAccount(player));
            } else {
              storage.setVerified();
            }
          });
        }
      }, 15);
    });

//    expensiveCheckIfAccountIsNew(player.getUniqueId(), accountIsNew -> {
//      if (accountIsNew) {
//        player.sendMessage("§cYour account is too new to play on this server.");
//      } else {
//        player.sendMessage("§aYour account is old enough to play on this server.");
//      }
//    });
  }

  public void expensiveCheckIfAccountIsNew(UUID id, BooleanConsumer callback) {
    if (!IntaveControl.GOMME_MODE) {
      return;
    }
//    isMicrosoftAccount(id, isMicrosoftAccount -> {
//      if (!IntaveControl.GOMME_MODE) {
//        return;
//      }
//      if (!isMicrosoftAccount) {
//        callback.accept(false);
//        return;
//      }
      seenOnLabyMod(id, seenOnLabyMod -> {
        if (!IntaveControl.GOMME_MODE) {
          return;
        }
        if (seenOnLabyMod) {
          callback.accept(false);
          return;
        }
        profileHasAnyOldReferences(id, profileHasAnyOldReferences -> {
          if (!IntaveControl.GOMME_MODE) {
            return;
          }
          if (profileHasAnyOldReferences) {
            callback.accept(false);
            return;
          }
          callback.accept(true);
        });
      });
//    });
  }

  @Native
  private void punishNewAccount(Player player) {
    if (IntaveControl.GOMME_MODE) {
      SibylBroadcast.broadcast(ChatColor.RED + player.getName() + " is a newly created account");
      User user = UserRepository.userOf(player);
      if (!user.meta().protocol().combatUpdate()) {
        user.nerfPermanently(AttackNerfStrategy.BLOCKING, "86");
        user.nerfPermanently(AttackNerfStrategy.GARBAGE_HITS, "86");
        user.nerfPermanently(AttackNerfStrategy.BURN_LONGER, "86");
      }
    }
  }

  private void profileHasAnyOldReferences(UUID playerId, BooleanConsumer callback) {
    String request = String.format(PROFILE_REQUEST_SCHEMA, playerId.toString());
    try {
      URL url = new URL(request);
      jsonRestRequest(url, jsonElement -> {
        JsonObject root = jsonElement.getAsJsonObject();
        boolean isValid = false;
        if (root.has("username_history")) {
          for (JsonElement usernameHistory : root.getAsJsonArray("username_history")) {
            JsonObject nameHistory = usernameHistory.getAsJsonObject();
            if (nameHistory.has("changed_at")) {
              // is in format 2023-01-16T23:28:53+00:00
              String changedAt = nameHistory.get("changed_at").getAsString();
              if (changedAt.contains("T")) {
                String[] split = changedAt.split("T");
                String date = split[0];
                if (dateOlderThan30Days(date)) {
                  isValid = true;
                  break;
                }
              }
            }
          }
        }
        if (!isValid && root.has("textures")) {
          JsonObject textures = root.getAsJsonObject("textures");
          if (textures.has("SKIN")) {
            for (JsonElement skinElement : textures.getAsJsonArray("SKIN")) {
              JsonObject skin = skinElement.getAsJsonObject();
              if (skin.has("first_seen_at")) {
                String firstSeenAt = skin.get("first_seen_at").getAsString();
                String[] split = firstSeenAt.split("T");
                String date = split[0];
                if (dateOlderThan30Days(date)) {
                  isValid = true;
                  break;
                }
              }
            }
          }
        }
        callback.accept(isValid);
      });
    } catch (Exception e) {
//      throw new RuntimeException(e);
//      e.printStackTrace();
    }
  }

  private void seenOnLabyMod(UUID playerId, BooleanConsumer callback) {
    String request = String.format(GET_GAME_STATS_REQUEST_SCHEMA, playerId.toString());
    try {
      URL url = new URL(request);
      jsonRestRequest(url, jsonElement -> {
        JsonObject root = jsonElement.getAsJsonObject();
        if (root.has("first_joined")) {
          String firstJoined = root.get("first_joined").getAsString();
          callback.accept(dateOlderThan30Days(firstJoined));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void isMicrosoftAccount(UUID playerId, BooleanConsumer callback) {
    String request = String.format(MSA_REQUEST_SCHEMA, playerId.toString());
    try {
      URL url = new URL(request);
      jsonRestRequest(url, jsonElement -> {
        JsonObject root = jsonElement.getAsJsonObject();
        if (root.has("type")) {
          callback.accept("MSA".equalsIgnoreCase(root.get("type").getAsString()));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void jsonRestRequest(URL url, Consumer<? super JsonElement> callback) {
    BackgroundExecutors.executeWhenever(() -> {
      try {
        URLConnection urlConnection = url.openConnection();
        urlConnection.setRequestProperty("User-Agent", "Mozilla/5.0");
        urlConnection.connect();
        InputStream inputStream = urlConnection.getInputStream();
        InputStreamReader reader = new InputStreamReader(inputStream);
        callback.accept(new JsonParser().parse(reader));
      } catch (Exception exception) {
//        exception.printStackTrace();
      }
    });
  }

  // input format is yyyy-MM-dd
  private static boolean dateOlderThan30Days(String date) {
    if (!date.contains("-")) {
      // very old
      return true;
    }
    if (date.contains("T")) {
      date = date.split("T")[0];
    }
    String[] split = date.split("-");
    if (split.length != 3) {
      return true;
    }
    int year = Integer.parseInt(split[0]);
    int month = Integer.parseInt(split[1]);
    int day = Integer.parseInt(split[2]);
    Calendar calendar = Calendar.getInstance();
    calendar.set(year, month - 1, day);
    calendar.add(Calendar.DAY_OF_MONTH, MINIMUM_AGE_IN_DAYS);
    return calendar.getTimeInMillis() < System.currentTimeMillis();
  }

  private interface BooleanConsumer {
    void accept(boolean value);
  }
}

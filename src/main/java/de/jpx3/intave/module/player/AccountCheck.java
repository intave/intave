package de.jpx3.intave.module.player;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.connect.sibyl.SibylBroadcast;
import de.jpx3.intave.executor.BackgroundExecutor;
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
    isMicrosoftAccount(id, isMicrosoftAccount -> {
      if (!IntaveControl.GOMME_MODE) {
        return;
      }
      if (!isMicrosoftAccount) {
        callback.accept(false);
        return;
      }
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
    });
  }

  @Native
  private void punishNewAccount(Player player) {
//    Synchronizer.synchronize(() -> {
//      String message = "&cDue to high recent abuse, your account needs to be at least few weeks old to join.";
//      player.sendMessage(message);
//      Synchronizer.synchronizeDelayed(() ->
//        player.kickPlayer(message), 3
//      );
//    });

    //
    // A quick moral detour why reducing combat impact of new players is both problematic and necessary.
    //
    /*
      Cheating has come to a point, where we can't really do anything about it anymore.
      The main problem right now is the influx of generated accounts that basically make Gomme a cracked server.
      The administration is very shy to implement verification, because of fears it could prevent normal players from participating in games.
      So, we have to find a solution by ourselves.
      The compromise is to reduce combat impact of new accounts, so that they can't do much too much damage.
      Our assumption is that an account that is only a month old will not stand a change against normal players anyway.
      New accounts will likely not use blocking and will not notice garbage hits, so it might not be a big deal for them.
      The biggest nerf probably is the Criticals-block, since it is extremely hard for cheaters to detect, and it reduces damage by a lot.
      This mechanic *has to be removed* when Gomme implements a proper verification system, that actually works.
      In the unlikely event that this system gets leaked to the public, we will not deny its existence, take full blame and open a public discussion about it.
     */
    if (IntaveControl.GOMME_MODE) {
      SibylBroadcast.broadcast(ChatColor.RED + player.getName() + " is a newly created account");
      User user = UserRepository.userOf(player);
      user.nerfPermanently(AttackNerfStrategy.BLOCKING, "86");
      user.nerfPermanently(AttackNerfStrategy.GARBAGE_HITS, "86");
      user.nerfPermanently(AttackNerfStrategy.BURN_LONGER, "86");
      user.setTrustFactor(TrustFactor.RED);
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
    BackgroundExecutor.execute(() -> {
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

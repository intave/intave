package de.jpx3.intave.access.player.trust;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.library.python.PythonTask;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.*;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static de.jpx3.intave.library.Python.*;

public final class DynamicStorageTrustfactorResolver implements TrustFactorResolver {
  private static final Resource STORAGE_SCRIPT =
    IntaveControl.USE_DEBUG_SCRIPT_RESOURCES ?
    Resources.resourceFromFile(new File(IntavePlugin.singletonInstance().dataFolder() + "/scripts/storage-dt/main.py")) :
    Resources.localServiceCacheResource("script/storage-dt/main.py", "sdx-main", TimeUnit.DAYS.toMillis(3));
  private static final Resource TREE_CSV = Resources.localServiceCacheResource("script/storage-dt/data.csv", "sdx-tree", TimeUnit.DAYS.toMillis(3));
  private static final PythonTask TASK = taskFromScript("storagetrust", prepareScript(STORAGE_SCRIPT, scriptAssetMapFrom("samples", TREE_CSV)));
  private static final String INPUT_FORMAT = "%s %s %s\n";

  @Override
  @Native
  public void resolve(Player player, Consumer<TrustFactor> callback) {
    User user = UserRepository.userOf(player);
    user.onStorageReady(storage -> callback.accept(calculateTrustfactorFor(storage)));
  }

  @Native
  private TrustFactor calculateTrustfactorFor(Storage storage) {
    try {
      PlayerStorage playerStorage = (PlayerStorage) storage;
      PlaytimeStorage playtimeStorage = playerStorage.storageOf(PlaytimeStorage.class);
      long joins = playtimeStorage.totalJoins();
      long hoursPlayed = playtimeStorage.minutesPlayed() / 60;
      long hoursAfk = playtimeStorage.minutesAfk() / 60;

      String output = TASK.feedLineAndRead(String.format(INPUT_FORMAT, hoursPlayed * 5, joins, hoursAfk)).trim();
      TrustFactor factor = TrustFactor.valueOf(output);
 
      ViolationStorage violationStorage = playerStorage.storageOf(ViolationStorage.class);
      StorageViolationEvents violations = violationStorage.violations();
      for (StorageViolationEvent violation : violations) {
        long timePassedSince = violation.timePassedSince();
        if ("heuristics".equalsIgnoreCase(violation.checkName())) {
          factor = factor.unsafer();
        } else if (timePassedSince < 1000 * 60 * 15) {
          factor = factor.unsafer();
        }
      }

      return factor;
    } catch (Exception exception) {
      exception.printStackTrace();
      return IntavePlugin.singletonInstance().trustFactorService().defaultTrustFactor();
    }
  }

  @Override
  public String toString() {
    return "DynamicAutoTrustfactor";
  }
}

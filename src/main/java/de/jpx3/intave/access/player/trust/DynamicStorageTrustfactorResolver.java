package de.jpx3.intave.access.player.trust;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.storage.*;
import org.bukkit.entity.Player;
import smile.classification.KNN;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.io.Arff;

import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class DynamicStorageTrustfactorResolver implements TrustFactorResolver {
//  private static final Resource STORAGE_SCRIPT =
//    IntaveControl.USE_DEBUG_SCRIPT_RESOURCES ?
//    Resources.resourceFromFile(new File(IntavePlugin.singletonInstance().dataFolder() + "/scripts/storage-dt/main.py")) :
//    Resources.localServiceCacheResource("script/storage-dt/main.py", "sdx-main", TimeUnit.DAYS.toMillis(3));
  private static final Resource TREE_ARFF = Resources.localServiceCacheResource("script/storage-dt/data.arff", "sdx-tree", TimeUnit.DAYS.toMillis(3));
//  private static final PythonTask TASK = taskFromScript("storagetrust", prepareScript(STORAGE_SCRIPT, scriptAssetMapFrom("samples", TREE_CSV)));
//  private static final String INPUT_FORMAT = "%s %s %s\n";

  private final KNN<double[]> classifier;

  {
    KNN<double[]> classifier;
    try {
      DataFrame frame;
      try (Arff arff = new Arff(new InputStreamReader(TREE_ARFF.read()))) {
        frame = arff.read();
      }
      double[][] x = new double[frame.size()][];
      int[] y = new int[frame.size()];
      for (int i = 0; i < frame.size(); i++) {
        Tuple tuple = frame.get(i);
        int a = tuple.getInt("a");
        int b = tuple.getInt("b");
        int c = tuple.getInt("c");
        x[i] = new double[] {a, b, c};
        y[i] = TrustFactor.valueOf(tuple.getString("t")).ordinal();
      }

      classifier = KNN.fit(x, y, 3);
    } catch (IOException | ParseException e) {
      classifier = null;
      e.printStackTrace();
    }
    this.classifier = classifier;
  }

  @Override
  @Native
  public void resolve(Player player, Consumer<TrustFactor> callback) {
    if (classifier == null) {
      return;
    }
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

      TrustFactor factor = TrustFactor.values()[classifier.predict(new double[] {hoursPlayed * 5, joins, hoursAfk})];
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

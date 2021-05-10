package de.jpx3.intave.detect;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class IntaveCheck implements EventProcessor {
  private final IntavePlugin plugin;
  private final String checkName;
  private final String configurationKey;
  private final CheckStatistics statistics;
  private final Map<TrustFactor, CheckStatistics> perTrustFactorStatistics;
  private final CheckConfiguration checkConfiguration;
  private final boolean enabled;

  public final List<IntaveCheckPart<?>> checkParts = new ArrayList<>();

  public IntaveCheck(String checkName, String configurationKey) {
    this.plugin = IntavePlugin.singletonInstance();
    this.checkName = checkName;
    this.configurationKey = configurationKey;
    this.statistics = new CheckStatistics();
    this.checkConfiguration = new CheckConfiguration(this);
    this.perTrustFactorStatistics = new EnumMap<>(TrustFactor.class);
    plugin.checkService().enterConfiguration(checkConfiguration);
    this.enabled = checkConfiguration.settings().boolBy("enabled");
  }

  protected User userOf(Player player) {
    return UserRepository.userOf(player);
  }

  protected void appendCheckPart(IntaveCheckPart<?> checkPart) {
    if(checkPart.parentCheck() != this) {
      throw new IntaveInternalException("Child lacks reference to parent");
    }
    checkParts.add(checkPart);
  }

  public int trustFactorSetting(String key, Player player) {
    String checkKey = configurationKey + "." + key;
    return plugin.trustFactorService().trustFactorSetting(checkKey, player);
  }

  public void statisticApply(User user, Consumer<CheckStatistics> applier) {
    applier.accept(baseStatistics());
    applier.accept(statisticsFor(user.trustFactor()));
  }

  public CheckStatistics baseStatistics() {
    return statistics;
  }

  private CheckStatistics statisticsFor(TrustFactor trustFactor) {
    return perTrustFactorStatistics.computeIfAbsent(trustFactor, x -> new CheckStatistics());
  }

  public String name() {
    return checkName;
  }

  public String configurationKey() {
    return configurationKey;
  }

  public CheckConfiguration configuration() {
    return checkConfiguration;
  }

  public List<IntaveCheckPart<?>> checkParts() {
    return checkParts;
  }

  public boolean enabled() {
    return enabled;
  }
}
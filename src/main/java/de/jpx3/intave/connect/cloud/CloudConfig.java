package de.jpx3.intave.connect.cloud;

import org.bukkit.configuration.ConfigurationSection;

public final class CloudConfig {
  private boolean enabled;
  private CloudFeatures features;

  public boolean isEnabled() {
    return this.enabled;
  }

  public CloudFeatures features() {
    return this.features;
  }

  public static CloudConfig from(ConfigurationSection section) {
    // enabled by default
    boolean enabled = section == null || section.getBoolean("enabled", true);
    ConfigurationSection featuresSection = section == null ? null : section.getConfigurationSection("features");
    boolean cloudStorage = featuresSection == null || featuresSection.getBoolean("storage", featuresSection.getBoolean("cloud-storage", true));
    boolean cloudTrustFactor = featuresSection == null || featuresSection.getBoolean("trustfactor", featuresSection.getBoolean("cloud-trustfactor", true));
    boolean cloudSamples = featuresSection == null || featuresSection.getBoolean("samples",  featuresSection.getBoolean("cloud-heuristics", true));
    boolean cloudLogs = false;
    CloudFeatures features = new CloudFeatures();
    features.cloudStorage = cloudStorage;
    features.cloudTrustFactor = cloudTrustFactor;
    features.cloudSamples = cloudSamples;
    features.cloudLogs = cloudLogs;
    CloudConfig config = new CloudConfig();
    config.enabled = enabled;
    config.features = features;
    return config;
  }

  public static class CloudFeatures {
    private boolean cloudStorage;
    private boolean cloudTrustFactor;
    private boolean cloudSamples;
    private boolean cloudLogs;

    public boolean cloudStorageEnabled() {
      return this.cloudStorage;
    }

    public boolean cloudTrustfactorEnabled() {
      return this.cloudTrustFactor;
    }

    public boolean sampleTransmission() {
      return this.cloudSamples;
    }

    public boolean isCloudLogs() {
      return this.cloudLogs;
    }
  }
}

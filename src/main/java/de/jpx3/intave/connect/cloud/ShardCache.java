package de.jpx3.intave.connect.cloud;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.connect.cloud.protocol.Shard;
import de.jpx3.intave.connect.cloud.protocol.Token;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Stream;

public final class ShardCache {
  private final Map<String, Shard> shards = new HashMap<>();
  private boolean wasUpdated = false;

  public ShardCache() {
    if (IntaveControl.CLOUD_LOCALHOST_MASTER_SHARD) {
      addShard("master", new Shard("master", "127.0.0.1", 2024, new Token(
        Base64.getUrlDecoder().decode("AAABABKT-HudAj2RnjKdCCcBTs3wWn5fVlOMSaexNfQjlDYjq8TK016swow9U36oYyz53ufgYv6PQPZo_2r5oQep04EedDyqUR3SrSyRXGBTEGcZRO79AOF7_7yF_iZtSliZhbWnJK7PDoIS3QzY6LW35A9G-IPlNM5GkHqR2ysJ8cP2635r9fVc0xU0iLKDXs6MPZdAPVpAVzdCnbezAyY3dQxSzufwWhANkrGsel-qxfZQSh7QVYsCt50-B7u8w2mwO9LbNvDK1zMyaJOlwrD6ztxEYwjmWv8y4LQbxEEfPaiTVB8X6tepDhLrmSO3Ql7QVsWyJS_Iwt7VSNYPaN56ptCF3FoKJw-d7ZGsaEbSUXk8AAAAUBeKwtkFQOkOJulQ7hn4ZtfwgFitc5nckFwfnXFGsYMgYdQyI7Yr1QyZbh8ZtzxAd-w0hX65ch9RnghmXixN6AzhHjrCzOqkD1N1swuIUvDhAAAAIL1zcMGcxnBgDvC-7hcFkDIm8WxwaWSnu2LfU4msGtES"),
        System.currentTimeMillis()
      )));
    } else {
      addShard("master", new Shard("master", "main.shard.intave.cloud", 2024, new Token(
        Base64.getUrlDecoder().decode("AAABABKT-HudAj2RnjKdCCcBTs3wWn5fVlOMSaexNfQjlDYjq8TK016swow9U36oYyz53ufgYv6PQPZo_2r5oQep04EedDyqUR3SrSyRXGBTEGcZRO79AOF7_7yF_iZtSliZhbWnJK7PDoIS3QzY6LW35A9G-IPlNM5GkHqR2ysJ8cP2635r9fVc0xU0iLKDXs6MPZdAPVpAVzdCnbezAyY3dQxSzufwWhANkrGsel-qxfZQSh7QVYsCt50-B7u8w2mwO9LbNvDK1zMyaJOlwrD6ztxEYwjmWv8y4LQbxEEfPaiTVB8X6tepDhLrmSO3Ql7QVsWyJS_Iwt7VSNYPaN56ptCF3FoKJw-d7ZGsaEbSUXk8AAAAUBeKwtkFQOkOJulQ7hn4ZtfwgFitc5nckFwfnXFGsYMgYdQyI7Yr1QyZbh8ZtzxAd-w0hX65ch9RnghmXixN6AzhHjrCzOqkD1N1swuIUvDhAAAAIL1zcMGcxnBgDvC-7hcFkDIm8WxwaWSnu2LfU4msGtES"),
        System.currentTimeMillis()
      )));
    }
  }

  public void addShard(Shard shard) {
    addShard(shard.name(), shard);
    wasUpdated = true;
  }

  public Shard masterShard() {
    return shards.get("master");
  }

  public boolean hasMasterShard() {
    return shards.containsKey("master");
  }

  public String masterCloudDomain() {
    return domainOf("master");
  }

  public String domainOf(String shard) {
    return shards.get(shard).domain();
  }

  public int masterCloudPort() {
    return portOf("master");
  }

  public int portOf(String shard) {
    return shards.get(shard).port();
  }

  public Token masterCloudToken() {
    return shards.get("master").token();
  }

  public Stream<String> compiledLines() {
    return shards.values().stream().map(shard -> {
      StringBuilder builder = new StringBuilder();
      builder.append(shard.name()).append(";");
      builder.append(shard.domain()).append(";");
      builder.append(shard.port()).append(";");
      StringWriter jsonStringWriter = new StringWriter();
      JsonWriter writer = new JsonWriter(jsonStringWriter);
      shard.token().serialize(writer);
      try {
        writer.flush();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      builder.append(jsonStringWriter);
      return builder.toString();
    });
  }

  public boolean wasModified() {
    return wasUpdated;
  }

  public static Collector<String, ?, ShardCache> resourceCollector() {
    return Collector.of(ShardCache::new, ShardCache::parseLine, ShardCache::merge);
  }

  private ShardCache merge(ShardCache shardCache) {
    shardCache.shards.forEach(this::addShard);
    return this;
  }

  private void addShard(String name, Shard shard) {
    shards.put(name, shard);
  }

  private void parseLine(String line) {
    String[] split = line.split(";");
    if (split.length != 4) {
      return;
    }
    String name = split[0];
    String domain = split[1];
    int port = Integer.parseInt(split[2]);
    String token = split[3];
    Shard shard = new Shard(name, domain, port, Token.from(new JsonReader(new StringReader(token))));
    addShard(name, shard);
  }
}

package de.jpx3.intave.fakeplayer;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.base.Preconditions;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.fakeplayer.movement.types.Movement;
import org.bukkit.entity.Player;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class FakePlayerBuilder {
  private Player parentPlayer = null;
  private int entityID = -1;
  private WrappedGameProfile wrappedGameProfile;
  private String tabListPrefix = "";
  private String prefix = "";
  private Movement movement = null;
  private int timeout = 10_000;
  private boolean invisible = false;
  private boolean visibleInTablist = true;
  private boolean equipArmor = true;
  private boolean equipHeldItem = true;
  private FakePlayerAttackSubscriber fakePlayerAttackSubscriber = () -> {
  };

  FakePlayerBuilder() {
  }

  public FakePlayerBuilder setParentPlayer(Player player) {
    this.parentPlayer = player;
    return this;
  }

  public FakePlayerBuilder setEntityID(int entityID) {
    this.entityID = entityID;
    return this;
  }

  public FakePlayerBuilder setTabListPrefix(String tabListPrefix) {
    this.tabListPrefix = tabListPrefix;
    return this;
  }

  public FakePlayerBuilder setPrefix(String prefix) {
    this.prefix = prefix;
    return this;
  }

  public FakePlayerBuilder setMovement(Movement movement) {
    this.movement = movement;
    return this;
  }

  public FakePlayerBuilder setTimeout(int timeout) {
    this.timeout = timeout;
    return this;
  }

  public FakePlayerBuilder setInvisible(boolean invisible) {
    this.invisible = invisible;
    return this;
  }

  public FakePlayerBuilder setInTablist(boolean visibleInTablist) {
    this.visibleInTablist = visibleInTablist;
    return this;
  }

  public FakePlayerBuilder setEquipArmor(boolean equipArmor) {
    this.equipArmor = equipArmor;
    return this;
  }

  public FakePlayerBuilder setEquipHeldItem(boolean equipHeldItem) {
    this.equipHeldItem = equipHeldItem;
    return this;
  }

  public FakePlayerBuilder setAttackSubscriber(FakePlayerAttackSubscriber subscriber) {
    this.fakePlayerAttackSubscriber = subscriber;
    return this;
  }

  public FakePlayerBuilder setGameProfile(WrappedGameProfile gameProfile) {
    this.wrappedGameProfile = gameProfile;
    return this;
  }

  public FakePlayer build() {
    Preconditions.checkNotNull(this.parentPlayer);
    Preconditions.checkState(this.entityID >= 0, "EntityId can not be negative!");
    Preconditions.checkNotNull(this.movement);
    return new FakePlayer(
      this.movement,
      this.parentPlayer,
      this.wrappedGameProfile == null ? createGameProfile() : this.wrappedGameProfile,
      this.tabListPrefix,
      this.prefix,
      this.entityID,
      this.timeout,
      this.invisible,
      this.visibleInTablist,
      this.equipArmor,
      this.equipHeldItem,
      this.fakePlayerAttackSubscriber
    );
  }

  private WrappedGameProfile createGameProfile() {
    UUID uuid;
    boolean noConnection = false;
    try {
      String url = "https://intave.de/api/randomplayeruuid.php";
      URLConnection connection = new URL(url).openConnection();
      connection.setUseCaches(false);
      connection.setDefaultUseCaches(false);
      connection.setReadTimeout(1000);
      connection.setConnectTimeout(1000);
      connection.addRequestProperty("User-Agent", "Mozilla/5.0");
      connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
      connection.addRequestProperty("Pragma", "no-cache");
      InputStream inputStream = connection.getInputStream();
      Scanner scanner = new Scanner(inputStream, "UTF-8");
      uuid = UUID.fromString(scanner.next());
    } catch (IOException e) {
      uuid = UUID.randomUUID();
      noConnection = true;
    }
    WrappedGameProfile wrappedGameProfile;
    if (noConnection) {
      String name = randomString();
      wrappedGameProfile = new WrappedGameProfile(uuid, name);
    } else {
      JSONObject jsonObject = connect(uuid);
      if (jsonObject == null) {
        String name = randomString();
        wrappedGameProfile = new WrappedGameProfile(uuid, name);
        return wrappedGameProfile;
      }
      String name = readNameFromJson(jsonObject);
      wrappedGameProfile = new WrappedGameProfile(uuid, name);
      applySkinToProfile(wrappedGameProfile, jsonObject);
    }
    return wrappedGameProfile;
  }

  private final static char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz".toCharArray();

  private String randomString() {
    StringBuilder stringBuilder = new StringBuilder();
    int length = ThreadLocalRandom.current().nextInt(5, 15);
    for (int i = 0; i < length; i++) {
      int index = ThreadLocalRandom.current().nextInt(1, ALPHABET.length);
      stringBuilder.append(ALPHABET[index - 1]);
    }
    return stringBuilder.toString();
  }

  private JSONObject connect(UUID uuid) {
    try {
      String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false";
      URLConnection connection = new URL(url).openConnection();
      connection.setUseCaches(false);
      connection.setDefaultUseCaches(false);
      connection.setReadTimeout(1000);
      connection.setConnectTimeout(1000);
      connection.addRequestProperty("User-Agent", "Mozilla/5.0");
      connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
      connection.addRequestProperty("Pragma", "no-cache");
      JSONParser jsonParser = new JSONParser();
      InputStream inputStream = connection.getInputStream();
      Scanner scanner = new Scanner(inputStream, "UTF-8");
      return (JSONObject) jsonParser.parse(scanner.useDelimiter("\\A").next());
    } catch (IOException | ParseException e) {
      return null;
    }
  }

  private String readNameFromJson(JSONObject jsonObject) {
    return (String) jsonObject.get("name");
  }

  private void applySkinToProfile(
    WrappedGameProfile wrappedGameProfile,
    JSONObject jsonObject
  ) {
    try {
      JSONArray properties = (JSONArray) jsonObject.get("properties");
      for (Object property : properties) {
        JSONObject object = (JSONObject) property;
        String value = (String) object.get("value");
        String signature = (String) object.get("signature");
        wrappedGameProfile.getProperties().put("textures", new WrappedSignedProperty("textures", value, signature));
      }
    } catch (Exception e) {
      throw new IntaveInternalException(e);
    }
  }
}

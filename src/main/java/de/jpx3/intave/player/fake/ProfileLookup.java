package de.jpx3.intave.player.fake;

import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
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

public final class ProfileLookup {
  public static WrappedGameProfile acquireGameProfile() {
    UUID uuid;
    boolean noConnection = false;
    try {
      String url = "https://service.intave.de/randomid";
      URLConnection connection = new URL(url).openConnection();
      connection.setUseCaches(false);
      connection.setDefaultUseCaches(false);
      connection.setReadTimeout(1000);
      connection.setConnectTimeout(1000);
      connection.addRequestProperty("User-Agent", "Intave/" + IntavePlugin.version());
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

  private static String randomString() {
    StringBuilder stringBuilder = new StringBuilder();
    int length = ThreadLocalRandom.current().nextInt(5, 15);
    for (int i = 0; i < length; i++) {
      int index = ThreadLocalRandom.current().nextInt(1, ALPHABET.length);
      stringBuilder.append(ALPHABET[index - 1]);
    }
    return stringBuilder.toString();
  }

  private static JSONObject connect(UUID uuid) {
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

  private static String readNameFromJson(JSONObject jsonObject) {
    return (String) jsonObject.get("name");
  }

  private static void applySkinToProfile(
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
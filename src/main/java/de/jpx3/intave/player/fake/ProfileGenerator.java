package de.jpx3.intave.player.fake;

import com.github.retrooper.packetevents.protocol.player.UserProfile;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ProfileGenerator {
  public static UserProfile acquireGameProfile() {
    return new UserProfile(UUID.randomUUID(), randomString());
  }

  private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz".toCharArray();

  private static String randomString() {
    StringBuilder stringBuilder = new StringBuilder();
    int length = ThreadLocalRandom.current().nextInt(5, 15);
    for (int i = 0; i < length; i++) {
      int index = ThreadLocalRandom.current().nextInt(1, ALPHABET.length);
      stringBuilder.append(ALPHABET[index - 1]);
    }
    return stringBuilder.toString();
  }

}

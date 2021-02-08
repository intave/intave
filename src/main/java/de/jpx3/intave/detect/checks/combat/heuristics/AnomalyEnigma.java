package de.jpx3.intave.detect.checks.combat.heuristics;

import com.google.common.collect.Lists;
import de.jpx3.intave.tools.annotate.Native;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public final class AnomalyEnigma {
  @Native
  public static String encryptAnomalies(List<Anomaly> anomalies) {
    List<String> usableAnomalies = anomalies.stream()
      .map(Anomaly::key)
      .distinct()
      .collect(Collectors.toList());
    String nonPadded = usableAnomalies
      .stream()
      .map((anomaly) -> encryptPattern(anomaly, usableAnomalies.size()))
      .collect(Collectors.joining());
    return encryptWithPadding(nonPadded, 6);
  }

  @Native
  private static String encryptPattern(String pattern, int size) {
    int subCheck = Integer.parseInt(pattern.substring(pattern.length() - 1));
    int mainCheck = Integer.parseInt(pattern.substring(0, pattern.length() - 1));
    if (mainCheck > 31) {
      throw new IllegalArgumentException("Invalid pattern key: main-check cannot be greater than 31");
    }
    if (subCheck > 7) {
      throw new IllegalArgumentException("Invalid pattern key: sub-check cannot be greater than 7");
    }
    int checkCombined = mainCheck << 3 | subCheck;
    checkCombined ^= 452938422 ^ 987509231 ^ size;
    for (int i = 0; i < size * 2; i++) {
      checkCombined ^= size * 28037423 * i;
      checkCombined ^= 928344123 * size;
      checkCombined ^= i * 4203874;
    }
    byte[] encode = Base64.getEncoder().encode(new byte[]{(byte) checkCombined});
    String result = new String(encode).replace("=", "");
    result = result.length() > 10 ? result.substring(0, 10) : result;
    return result;
  }

  @Native
  private static String encryptWithPadding(String pattern, int paddingLength) {
    if (paddingLength % 2 != 0) {
      throw new IllegalArgumentException("Padding length cannot be odd");
    }
    boolean exceededLength = pattern.length() >= paddingLength - 2;
    int endingGarbageCharacters = exceededLength ? -1 : paddingLength - pattern.length();
    endingGarbageCharacters ^= pattern.charAt(0);
    String first = new String(Base64.getEncoder().encode(new byte[]{(byte) endingGarbageCharacters}));
    first = first.replace("=", "");
    if (pattern.length() >= paddingLength - 2) {
      return first + pattern;
    }
    StringBuilder patternStringBuilder = new StringBuilder();
    patternStringBuilder.append(first);
    patternStringBuilder.append(pattern);
    while (patternStringBuilder.length() < paddingLength) {
      int garbageCharacter = Math.max(1, new SecureRandom().nextInt(64));
      String garbage = new String(Base64.getEncoder().encode(new byte[]{(byte) garbageCharacter}));
      garbage = garbage.replace("=", "");
      patternStringBuilder.append(garbage);
    }
    return patternStringBuilder.toString();
  }

  @Native
  public static String decryptPatterns(String patterns) {
    patterns = decryptWithPadding(patterns);
    int size = patterns.length() / 2;
    List<String> decryptedPatterns = Lists.newArrayList();
    while (patterns.length() > 0) {
      if (patterns.length() % 2 == 0) {
        String pattern = patterns.substring(0, 2);
        String decrypted = decryptPattern(pattern, size);
        decryptedPatterns.add("p[" + decrypted + "]");
      }
      patterns = patterns.substring(1);
    }
    return String.join(",", decryptedPatterns);
  }

  @Native
  private static String decryptWithPadding(String pattern) {
    String paddingLength = pattern.substring(0, 2);
    int paddingKey = (Base64.getDecoder().decode(paddingLength)[0]) ^ pattern.charAt(2);
    return pattern.substring(2, paddingKey > 0 ? pattern.length() - paddingKey + 2 : pattern.length());
  }

  @Native
  private static String decryptPattern(String pattern, int size) {
    byte[] encode = Base64.getDecoder().decode(pattern);
    int checkCombined = encode[0];
    for (int i = 0; i < size * 2; i++) {
      checkCombined ^= size * 28037423 * i;
      checkCombined ^= 928344123 * size;
      checkCombined ^= i * 4203874;
    }
    checkCombined ^= 452938422 ^ 987509231 ^ size;
    int subCheck = checkCombined & 0b111;
    int mainCheck = (checkCombined & 0b11111000) >> 3;
    return mainCheck + String.valueOf(subCheck);
  }
}
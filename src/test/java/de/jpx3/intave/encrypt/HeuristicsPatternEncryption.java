package de.jpx3.intave.encrypt;

import de.jpx3.intave.detect.checks.combat.heuristics.Anomaly;
import de.jpx3.intave.detect.checks.combat.heuristics.Confidence;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

public final class HeuristicsPatternEncryption {
  @Test
  public void testPatternEncryption() {
    List<Anomaly> anomalies = Arrays.asList(
      Anomaly.anomalyOf("85", Confidence.MAYBE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("85", Confidence.MAYBE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("85", Confidence.MAYBE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("85", Confidence.MAYBE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("85", Confidence.MAYBE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("122", Confidence.PROBABLE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("122", Confidence.PROBABLE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("122", Confidence.PROBABLE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("122", Confidence.PROBABLE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("121", Confidence.PROBABLE, Anomaly.Type.KILLAURA, "description", 0),
      Anomaly.anomalyOf("121", Confidence.PROBABLE, Anomaly.Type.KILLAURA, "description", 0)
    );

    anomalies = anomaliesForId(anomalies);

    String encryptAnomalies = encryptAnomalies(anomalies);
    System.out.println("encrypted:" + encryptAnomalies);

    String decryptPatterns = decryptPatterns(encryptAnomalies);
    System.out.println("decrypted:" + decryptPatterns);

    String expectedResult = anomalies.stream().map(Anomaly::key).distinct().map(x -> "p[" + x + "]").collect(Collectors.joining(" "));
    Assertions.assertEquals(expectedResult, decryptPatterns);
  }

  @Test
  public void test() {
    System.out.println(decryptPatterns("NMWWRhQk9C"));
  }

  private List<Anomaly> anomaliesForId(List<Anomaly> anomalies) {
    // Remove anomalies without effect
    anomalies.removeIf(anomaly -> anomaly.confidence() == Confidence.NONE);

    // Remove duplicated anomalies
    List<String> knownPatterns = new ArrayList<>();
    List<Anomaly> suitableAnomalies = new ArrayList<>();

    for (Anomaly anomaly : anomalies) {
      String pattern = anomaly.key();
      if (!knownPatterns.contains(pattern)) {
        knownPatterns.add(pattern);
        suitableAnomalies.add(anomaly);
      }
    }

    anomalies = suitableAnomalies;

    // Format anomalies after their priority
    if (anomalies.size() > 2) {
      anomalies.sort(Comparator.comparingInt(o -> o.confidence().level()));
      Collections.reverse(anomalies);
      List<Anomaly> reducedAnomalies = new ArrayList<>();
      for (int i = 0; i <= 1; i++) {
        reducedAnomalies.add(anomalies.get(i));
      }
      anomalies = reducedAnomalies;
    }
    return anomalies;
  }

  private String encrypt0(String string) {
    char characterA = (char) Base64.getEncoder().encode(new byte[] {(byte) new SecureRandom().nextInt(0b111111)})[0];
    char characterB = (char) Base64.getEncoder().encode(new byte[] {(byte) new SecureRandom().nextInt(0b111111)})[0];
    byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
    for (int i = 0; i < bytes.length; i++) {
      int key = characterA ^ characterB % (i + 1 ^ characterB * 5);
      bytes[i] ^= key;
    }
    String encode = new String(Base64.getEncoder().encode(bytes));
    encode = encode.replace("=", "");
    return String.valueOf(characterA) + characterB + encode;
  }

  private String decrypt0(String string) {
    char characterA = string.charAt(0);
    char characterB = string.charAt(1);
    string = string.substring(2);
    byte[] bytes = Base64.getDecoder().decode(string);
    for (int i = 0; i < bytes.length; i++) {
      int key = characterA ^ characterB % (i + 1 ^ characterB * 5);
      bytes[i] ^= key;
    }
    return new String(bytes);
  }

  private String encryptAnomalies(List<Anomaly> anomalies) {
    List<String> usableAnomalies = anomalies.stream()
      .map(Anomaly::key)
      .distinct()
      .collect(Collectors.toList());
    String nonPadded = usableAnomalies
      .stream()
      .map((anomaly) -> encryptPattern(anomaly, usableAnomalies.size()))
      .collect(Collectors.joining());
    return encrypt0(encryptWithPadding(nonPadded, 6));
  }

  private String encryptPattern(String pattern, int size) {
    // 3 bits sub-check   -> 7    = 0 0 0 0 0 1 1 1
    // 5 bits check       -> 31   = 1 1 1 1 1 0 0 0
    // >= 33 && <= 126
    int subCheck = Integer.parseInt(pattern.substring(pattern.length() - 1));
    int mainCheck = Integer.parseInt(pattern.substring(0, pattern.length() - 1));
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

  private String encryptWithPadding(String pattern, int paddingLength) {
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

  private String decryptPatterns(String patterns) {
    patterns = decryptWithPadding(decrypt0(patterns));
//    patterns = decrypt0(patterns);
    int size = patterns.length() / 2;
    List<String> decryptedPatterns = new ArrayList<>();
    while (patterns.length() > 0) {
      if (patterns.length() % 2 == 0) {
        String pattern = patterns.substring(0, 2);
        String decrypted = decryptPattern(pattern, size);
        decryptedPatterns.add("p[" + decrypted + "]");
      }
      patterns = patterns.substring(1);
    }
    return String.join(" ", decryptedPatterns);
  }

  private String decryptWithPadding(String pattern) {
    String paddingLength = pattern.substring(0, 2);
    int paddingKey = (Base64.getDecoder().decode(paddingLength)[0]) ^ pattern.charAt(2);
    return pattern.substring(2, paddingKey > 0 ? pattern.length() - paddingKey + 2 : pattern.length());
  }

  private String decryptPattern(String pattern, int size) {
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
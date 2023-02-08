package de.jpx3.intave.connect.sibyl;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class SibylCensor {
  public static void main(String[] args) {
    String password = "cMT?UqSLyvURw2u?bF)PqlJLssW%;QKd";
    String censoring = "Text is " + censoring("Hello %s, how %s you?", new String[]{"World", "are"}, password);
    System.out.println(censoring);
    System.out.println(uncensoring(censoring, password));
  }

  public static String thisPlease(String message, Object... args) {
    String[] stringArgs = new String[args.length];
    for (int i = 0; i < args.length; i++) {
      stringArgs[i] = args[i].toString();
    }
    return replaceArgs(message, stringArgs);
  }

  private static String censoring(String message, String[] args, String password) {
    byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
    byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
    try {
      // encrypt with AES
      Cipher cipher = Cipher.getInstance("AES");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(passwordBytes, "AES"));
      StringBuilder argsString = new StringBuilder();
      for (String arg : args) {
        argsString.append(arg.replace(";", "MANUAL_SEMIKOLON")).append(";");
      }
      return "*+*" + Base64.getEncoder().encodeToString(cipher.doFinal(bytes)) + "^" + argsString + "*+*";
    } catch (Exception exception) {
      exception.printStackTrace();
      return "*+*ERROR*+*";
    }
  }

  private static String uncensoring(String input, String password) {
    // find sequence "*+*TEXT^TEXT;TEXT;TEXT*+*" by *+* and *+*
    String k = input.substring(input.indexOf("*+*") + 3);
    String content = k.substring(0, k.indexOf("*+*"));
//    String content = input.substring(3, input.length() - 3);
    if ("ERROR".equals(content)) {
      return "ERROR";
    }
    String[] split = content.split("\\^");
    String encrypted = split[0];
    String[] args = split[1].split(";");
    for (int i = 0; i < args.length; i++) {
      args[i] = args[i].replace("MANUAL_SEMIKOLON", ";");
    }
    byte[] bytes = Base64.getDecoder().decode(encrypted);
    byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
    try {
      // decrypt with AES
      Cipher cipher = Cipher.getInstance("AES");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(passwordBytes, "AES"));
      String replaced = replaceArgs(new String(cipher.doFinal(bytes), StandardCharsets.UTF_8), args);
      return input.replace("*+*" + content + "*+*", replaced);
    } catch (Exception exception) {
      exception.printStackTrace();
      return "ERROR";
    }
  }

  private static String replaceArgs(String message, String[] args) {
    for (String arg : args) {
      message = message.replaceFirst("%s", arg);
    }
    return message;
  }
}

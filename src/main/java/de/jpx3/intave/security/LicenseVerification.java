package de.jpx3.intave.security;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.tools.annotate.Native;

import java.io.InputStream;
import java.util.Scanner;

public final class LicenseVerification {
  private static String licenseName;
  private static String networkName;

  @Native
  public static String network() {
    return System.getProperty("8ugyoiodfg");
  }

  @Native
  public static String licenseKey() {
    String rawLicense = rawLicense();
    return rawLicense.substring(4, Math.min(9, rawLicense.length()));
  }

  @Native
  public static String rawLicense() {
    if(licenseName == null) {
      if(IntaveControl.DISABLE_LICENSE_CHECK) {
        licenseName = "TkxzRWpMdE1NVmdCUUdOMjdmNmdTdz09yB1f45kTpS5yiTeuw6DrRQ==";// Intavede
      } else {
        InputStream resourceAsStream = LicenseVerification.class.getResourceAsStream("/5ee6db6d-6751-4081-9cbf-28eb0f6cc055");
        StringBuilder stringBuilder = new StringBuilder();
        Scanner scanner = new Scanner(resourceAsStream);
        while (scanner.hasNext()) {
          stringBuilder.append(scanner.next());
        }
        licenseName = stringBuilder.toString();
      }
    }
    return licenseName;
  }
}

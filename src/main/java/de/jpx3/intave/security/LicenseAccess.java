package de.jpx3.intave.security;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.annotate.Native;

import java.io.InputStream;
import java.util.Scanner;

import static de.jpx3.intave.library.asm.ClassVisitor.LICENSE_NAME;

public final class LicenseAccess {
  private static String licenseName;
  private static String networkName;

  @Native
  public static String network() {
    return LICENSE_NAME;//System.getProperty("java.net.serviceprovider.key");
  }

  @Native
  public static String licenseKey() {
    String rawLicense = rawLicense();
    return rawLicense.substring(4, Math.min(9, rawLicense.length()));
  }

  @Native
  public static String rawLicense() {
    if (licenseName == null) {
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        if (IntaveControl.GOMME_MODE) {
          licenseName = "srXcRrWOW9kO0edEdrtUsxPkWYFbTcWf55mKk4KHAfxK7P0k0tOTOxBnMDMCO33GMcABC2eAHuNSBYe0wnYTkWUvQrptoBsTZenAIIIIIYTE4ZmE4MjQyYmU3YzIyNDd"; // GommeHDnet
        } else {
//          licenseName = "TkxzRWpMdE1NVmdCUUdOMjdmNmdTdz09yB1f45kTpS5yiTeuw6DrRQ==";// Intavede
          licenseName = "dW9b4SrAMxc5hSfbbp9xawEOrXV47DpHezU5nM8Dfbx2nON72AzA2PFEbfSh6HChYeqKvRAVqMnkMUm36AKKWtPcz706drbT57stIIIIIMTQwYWI3YmMyMzU0NTRmM2M"; // Intavede
        }
      } else {
        InputStream resourceAsStream = LicenseAccess.class.getResourceAsStream("/5ee6db6d-6751-4081-9cbf-28eb0f6cc055");
        if (resourceAsStream == null) {
          throw new IntaveInternalException("Failed to locate identification file");
        }
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

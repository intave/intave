package de.jpx3.intave.security;

import de.jpx3.intave.tools.annotate.Native;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;

public final class SSLConnectionVerifier {

  @Native
  public static void verifyURLConnection(HttpsURLConnection connection) throws IOException {
//    Certificate serverCertificate = connection.getServerCertificates()[0];
//    try {
//      byte[] fabulousToken = {48, -126, 1, 34, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5, 0, 3, -126, 1, 15, 0, 48, -126, 1, 10, 2, -126, 1, 1, 0, -25, 51, 69, 34, -79, -106, 17, 69, -11, -2, 107, -86, 24, -71, 76, -92, -1, 18, -70, -60, -52, -36, -30, 30, -39, -92, -105, 113, 81, -77, 29, 1, -79, -52, 5, -126, 114, 104, -81, 108, 86, -122, -95, -31, -5, 5, -7, -51, -20, 25, -43, -17, -95, 36, -1, -79, 4, 60, 10, -53, 9, -73, -48, 12, -58, -93, -90, -105, 63, -112, 121, -2, -27, -9, 89, -101, 2, 6, -58, -36, 17, 47, -126, -13, -82, -59, -121, -104, -89, 103, -30, -98, 89, 10, 74, 69, 41, 54, 89, 112, -114, -73, 42, 13, -8, 104, -67, 70, 6, 5, -113, 20, -39, 67, -80, 29, 90, 24, 101, -54, 13, 44, 123, -35, 104, -98, -21, -27, -124, -71, 121, -112, 97, -127, 65, 107, 40, -91, -74, -82, 118, 21, -32, 34, -113, 124, 61, -14, -32, 42, 116, -69, -64, 113, 73, -72, -109, 10, -10, -103, -71, -74, 120, -127, -127, 89, 80, 102, 6, 63, 1, -98, 43, 10, 68, 30, 25, 94, 14, 110, -86, 4, -35, -51, 6, 104, 48, -55, 0, 38, -94, -77, 98, 56, 13, 65, 95, 99, -25, -13, -87, 55, -119, -2, 22, -55, -5, 7, 78, -126, 36, 96, -31, -72, -54, -9, -58, 17, -51, -3, -90, -51, -43, -108, 27, -91, 4, -28, 6, 53, -97, -33, 57, 52, -51, -122, 127, 95, 103, 95, 29, -94, -10, -27, 120, -105, -99, -39, 79, -105, -30, 62, 101, 40, 102, 113, 2, 3, 1, 0, 1};
//
//      if(serverCertificate.)
//      if(!Arrays.equals(fabulousToken, serverCertificate.getPublicKey().getEncoded())) {
//
//        throw new IllegalStateException();
//      }
//    } catch (Exception e) {
//      throw new SecurityException("Invalid connection", e);
//    }
  }
}

package de.jpx3.relocator;

import de.jpx3.intave.test.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

public final class RelocatorConnectionTests {
  private static final String VERSION = "14.5.0";
  private static final String URL_TARGET_LAYOUT = "https://%s/relocate/download?id=%s";
  private static final String URL_VERIFY_LAYOUT = "https://%s/relocate/verify?id=%s";

  public static void main(String[] args) {
    try {
      new RelocatorConnectionTests().connect();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Test
  public void connect() throws IOException {
    String domain = "service.intave.de";

    String targetURL = String.format(URL_TARGET_LAYOUT, domain, "TkxzRWpMdE1NVmdCUUdOMjdmNmdTdz09yB1f45kTpS5yiTeuw6DrRQ==");
    URL request = new URL(targetURL);

    URLConnection connection = request.openConnection();
    connection.addRequestProperty("User-Agent", "Intave/" + VERSION);
    connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
    connection.setUseCaches(false);
    connection.addRequestProperty("Pragma", "no-cache");
    connection.setRequestProperty("A", "5207371e1ee667688990f227d84462b80ad09eee9d8d9034413f49008f3ac503");
    connection.setRequestProperty("B", VERSION);
    connection.setConnectTimeout(20000);
    connection.setReadTimeout(20000);
    connection.connect();

    // print response

    InputStream inputStream = connection.getInputStream();
    Scanner scanner = new Scanner(inputStream);
    while (scanner.hasNextLine()) {
      System.out.println(scanner.nextLine());
    }
    scanner.close();
  }
}

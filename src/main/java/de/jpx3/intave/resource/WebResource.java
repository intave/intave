package de.jpx3.intave.resource;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.security.LicenseAccess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.*;

final class WebResource implements Resource {
  private final URL url;
  private final Resource fallback;

  public WebResource(String url) throws MalformedURLException {
    this(new URL(url), null);
  }

  public WebResource(String url, @Nullable Resource fallback) throws MalformedURLException {
    this(new URL(url), fallback);
  }

  public WebResource(URL url) {
    this(url, null);
  }

  public WebResource(URL url, @Nullable Resource fallback) {
    this.url = url;
    this.fallback = fallback;
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public long lastModified() {
    return System.currentTimeMillis();
  }

  @Override
  public void write(InputStream inputStream) {
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream read() {
    boolean debug = System.getProperty("intave.kdebug", "NA").equalsIgnoreCase("UPSFF0Y8Y7H4UJQL8QCRSI857S4DVBKS");
    try {
      URLConnection connection = url.openConnection();
      connection.addRequestProperty("User-Agent", "Intave/" + IntavePlugin.version());
      connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
      connection.addRequestProperty("Pragma", "no-cache");
      connection.addRequestProperty("Identifier", LicenseAccess.rawLicense());
      connection.setConnectTimeout(3000);
      connection.setReadTimeout(3000);
      InputStream inputStream = connection.getInputStream();
      // forcing stream read
      if (inputStream.available() == 0) {
        byte[] buff = new byte[4096];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int i;
        while ((i = inputStream.read(buff)) != -1) {
          output.write(buff, 0, i);
        }
        byte[] data = output.toByteArray();
        if (IntaveControl.DEBUG || debug) {
          System.out.println("[debug] Read " + data.length + " bytes from " + url + " manually");
        }
        return new ByteArrayInputStream(data);
      }
      if (IntaveControl.DEBUG || debug) {
        System.out.println("[debug] Read " + inputStream.available() + " bytes from " + url);
      }
      return inputStream;
    } catch (SocketTimeoutException timeout) {
      if (IntaveControl.DEBUG || debug) {
        System.out.println("[debug] Timeout reading " + url);
      }
      if (fallback != null) {
        return fallback.read();
      }
      return new ByteArrayInputStream(new byte[0]);
    } catch (UnknownHostException host) {
      if (IntaveControl.DEBUG || debug) {
        System.out.println("[debug] Unable to connect to " + url);
      }
      if (fallback != null) {
        return fallback.read();
      }
      return new ByteArrayInputStream(new byte[0]);
    } catch (Exception exception) {
      if (IntaveControl.DEBUG || debug) {
        System.out.println("[debug] Unable to read " + url);
        exception.printStackTrace();
      }
      if (fallback != null) {
        return fallback.read();
      }
      return new ByteArrayInputStream(new byte[0]);
    }
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException();
  }
}

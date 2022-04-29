package de.jpx3.intave.resource;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.security.LicenseAccess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;

public final class WebResource implements Resource {
  private final URL url;

  public WebResource(String url) throws MalformedURLException {
    this(new URL(url));
  }

  public WebResource(URL url) {
    this.url = url;
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
    try {
      URLConnection connection = url.openConnection();
      connection.addRequestProperty("User-Agent", "Intave/" + IntavePlugin.version());
      connection.addRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
      connection.addRequestProperty("Pragma", "no-cache");
      connection.addRequestProperty("Identifier", LicenseAccess.rawLicense());
      connection.setConnectTimeout(4000);
      connection.setReadTimeout(4000);
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
        if (IntaveControl.DISABLE_LICENSE_CHECK) {
          System.out.println("[debug] Read " + data.length + " bytes from " + url + " manually");
        }
        return new ByteArrayInputStream(data);
      }
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        System.out.println("[debug] Read " + inputStream.available() + " bytes from " + url);
      }
      return inputStream;
    } catch (SocketTimeoutException timeout) {
      if (IntaveControl.DISABLE_LICENSE_CHECK) {
        System.out.println("[debug] Timeout reading " + url);
      }
      return new ByteArrayInputStream(new byte[0]);
    } catch (Exception exception) {
      exception.printStackTrace();
      return new ByteArrayInputStream(new byte[0]);
    }
  }

  @Override
  public void delete() {
    throw new UnsupportedOperationException();
  }
}

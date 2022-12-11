package de.jpx3.intave.module.player;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.jpx3.intave.annotate.Native;
import de.jpx3.intave.security.LicenseAccess;
import de.jpx3.intave.user.storage.Storage;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import static java.util.zip.Deflater.BEST_COMPRESSION;

public final class StorageIOProcessor {
  @Native
  public static void inputTo(Storage storage, ByteBuffer buffer) {
    if (buffer == null) {
      return;
    }
    byte[] array = buffer.array();
    if (array.length == 0) {
      return;
    }
    byte[] bytes = new byte[0];
    try {
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      Random random = new Random(LicenseAccess.network().hashCode());
      byte[] password = new byte[32];
      random.nextBytes(password);
      SecretKeySpec key = new SecretKeySpec(password, "AES");
      cipher.init(Cipher.DECRYPT_MODE, key);
      byte[] decrypted = cipher.doFinal(array);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStream outputStream = out;
      outputStream = new InflaterOutputStream(outputStream, new Inflater());
      outputStream.write(decrypted);
      outputStream.flush();
      outputStream.close();
      bytes = out.toByteArray();
    } catch (Exception exception) {
//      exception.printStackTrace();
    }
    storage.readFrom(ByteStreams.newDataInput(bytes));
  }

  @Native
  public static ByteBuffer outputFrom(Storage storage) {
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    storage.writeTo(output);
    byte[] bytes = output.toByteArray();
    byte[] compress = new byte[0];
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      OutputStream outputStream = out;
      outputStream = new DeflaterOutputStream(outputStream, new Deflater(BEST_COMPRESSION));
      outputStream.write(bytes);
      outputStream.flush();
      outputStream.close();
      byte[] imm = out.toByteArray();
      Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
      Random random = new Random(LicenseAccess.network().hashCode());
      byte[] password = new byte[32];
      random.nextBytes(password);
      SecretKeySpec key = new SecretKeySpec(password, "AES");
      cipher.init(Cipher.ENCRYPT_MODE, key);
      compress = cipher.doFinal(imm);
    } catch (Exception exception) {
//      exception.printStackTrace();
    }
    return ByteBuffer.wrap(compress);
  }
}

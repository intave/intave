package de.jpx3.intave.security;

import de.jpx3.intave.tools.annotate.Native;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashAccess {
  @Native
  public static String hashOf(File file) {
    StringBuilder jarChecksum = new StringBuilder();
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");// MD5
      FileInputStream fis = new FileInputStream(file);
      byte[] dataBytes = new byte[1024];
      int nread;
      while ((nread = fis.read(dataBytes)) != -1) {
        md.update(dataBytes, 0, nread);
      }
      byte[] mdbytes = md.digest();
      for (byte mdbyte : mdbytes) {
        jarChecksum.append(Integer.toString((mdbyte & 0xff) + 0x100, 16).substring(1));
      }
    } catch (NoSuchAlgorithmException | IOException exception) {
      exception.printStackTrace();
      jarChecksum.append("~invalid");
    }
    return jarChecksum.toString();
  }

//  @Native
//  public static void push(FileInputStream stream, MessageDigest digest) {
//    byte[] dataBytes = new byte[1024];
//    int nread;
//    try {
//      while ((nread = stream.read(dataBytes)) != -1) {
//        digest.update(dataBytes, 0, nread);
//      }
//    } catch (Exception exception) {
//      throw new IntaveInternalException(exception);
//    }
//  }
}

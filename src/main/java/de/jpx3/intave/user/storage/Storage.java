package de.jpx3.intave.user.storage;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import static java.util.zip.Deflater.BEST_COMPRESSION;

public interface Storage {
  void writeTo(ByteArrayDataOutput output);
  void readFrom(ByteArrayDataInput input);
}

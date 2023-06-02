package de.jpx3.intave.resource;

import java.io.IOException;
import java.io.OutputStream;

public final class OutputStreamMultiplexer extends OutputStream {
  private final OutputStream[] streams;

  public OutputStreamMultiplexer(OutputStream... streams) {
    this.streams = streams;
  }

  @Override
  public void write(int b) throws IOException {
    for (OutputStream stream : streams) {
      stream.write(b);
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    for (OutputStream stream : streams) {
      stream.write(b);
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    for (OutputStream stream : streams) {
      stream.write(b, off, len);
    }
  }

  @Override
  public void flush() throws IOException {
    for (OutputStream stream : streams) {
      stream.flush();
    }
  }


  @Override
  public void close() throws IOException {
    for (OutputStream stream : streams) {
      stream.close();
    }
  }

  public static OutputStreamMultiplexer of(OutputStream... streams) {
    return new OutputStreamMultiplexer(streams);
  }
}

package de.jpx3.intave.module.nayoro;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

final class MultiplexOutputStream extends OutputStream {
  private final OutputStream[] outputStreams;

  public MultiplexOutputStream(OutputStream... outputStreams) {
    this.outputStreams = outputStreams;
  }

  @Override
  public void write(int b) throws IOException {
    for (OutputStream os : outputStreams) {
      os.write(b);
    }
  }

  @Override
  public void write(byte @NotNull [] b) throws IOException {
    for (OutputStream os : outputStreams) {
      os.write(b);
    }
  }

  @Override
  public void write(byte @NotNull [] b, int off, int len) throws IOException {
    for (OutputStream os : outputStreams) {
      os.write(b, off, len);
    }
  }

  @Override
  public void flush() throws IOException {
    for (OutputStream os : outputStreams) {
      os.flush();
    }
  }

  @Override
  public void close() throws IOException {
    for (OutputStream os : outputStreams) {
      os.close();
    }
  }
}

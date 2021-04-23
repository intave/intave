package de.jpx3.intave.diagnostics.report;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class DataStruct<T extends DataStruct<?>> {
  public abstract void load(InputStream inputStream);
  public abstract void merge(T base);
  public abstract void save(OutputStream outputStream);
}

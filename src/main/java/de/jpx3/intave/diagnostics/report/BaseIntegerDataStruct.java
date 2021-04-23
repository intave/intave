package de.jpx3.intave.diagnostics.report;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public final class BaseIntegerDataStruct extends DataStruct<BaseIntegerDataStruct> {
  private Map<String, Integer> data = new HashMap<>();

  @Override
  public void load(InputStream inputStream) {

  }

  @Override
  public void merge(BaseIntegerDataStruct base) {

  }

  @Override
  public void save(OutputStream outputStream) {

  }
}

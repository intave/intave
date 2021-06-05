package de.jpx3.intave.diagnostics.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class AccuracyReport extends Report {
  public AccuracyReport() {
    super("accuracy");
  }

  @Override
  public void push(ByteArrayInputStream input) {

  }

  @Override
  public ByteArrayOutputStream pull(ByteArrayInputStream input) {




    return new ByteArrayOutputStream();
  }
}

package de.jpx3.intave.diagnostics.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class PerformanceReport extends Report {
  public PerformanceReport() {
    super("performance");
  }

  @Override
  public void push(ByteArrayInputStream input) {

  }

  @Override
  public ByteArrayOutputStream pull(ByteArrayInputStream input) {
    return new ByteArrayOutputStream();
  }
}

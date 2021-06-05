package de.jpx3.intave.diagnostics.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class Report {
  private final String name;

  public Report(String name) {
    this.name = name;
  }

  public void push(ByteArrayInputStream input) {

  }

  public ByteArrayOutputStream pull(ByteArrayInputStream input) {
    throw new UnsupportedOperationException("Report does not support saving?");
  }

  public final String name() {
    return name;
  }
}

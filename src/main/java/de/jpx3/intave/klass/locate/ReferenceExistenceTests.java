package de.jpx3.intave.klass.locate;

import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.test.Tests;

public final class ReferenceExistenceTests extends Tests {
  public ReferenceExistenceTests() {
    super("CRT");
  }

  @Test(
    testCode = "A",
    severity = Severity.ERROR
  )
  public void checkClasses() {
    ClassLocations classLocations = Locate.classLocations();
    for (ClassLocation classLocation : classLocations) {
      try {
        classLocation.access();
      } catch (Exception exception) {
        System.out.println("Failed to access " + classLocation);
      }
    }
  }

  @Test(
    testCode = "B",
    severity = Severity.ERROR
  )
  public void checkMethods() {

  }

  @Test(
    testCode = "C",
    severity = Severity.ERROR
  )
  public void checkFields() {

  }
}

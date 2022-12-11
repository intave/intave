package de.jpx3.intave.klass.locate;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;
import de.jpx3.intave.test.Tests;

public final class ReferenceExistenceTests extends Tests {
  public ReferenceExistenceTests() {
    super("RE");
  }

  @Test(
    testCode = "A",
    severity = Severity.ERROR
  )
  public void checkClasses() {
    for (ClassLocation classLocation : Locate.classLocations()) {
      try {
        String className = classLocation.access().getName();
      } catch (Exception exception) {
        IntaveLogger.logger().warn("Class " + classLocation.compiledLocation() + " does not exist");
//        exception.printStackTrace();
        throw exception;
      }
    }
  }

  @Test(
    testCode = "B",
    severity = Severity.ERROR
  )
  public void checkMethods() {
    for (MethodLocation methodLocation : Locate.methodLocations()) {
      try {
        methodLocation.access();
      } catch (Exception exception) {
        IntaveLogger.logger().warn("Method " + methodLocation.methodNameOfKey() + "@" + methodLocation.targetMethodName() + methodLocation.targetMethodSignature() + " in class " + methodLocation.classKey() + " does not exist");
//        exception.printStackTrace();
        throw exception;
      }
    }

  }

  @Test(
    testCode = "C",
    severity = Severity.ERROR
  )
  public void checkFields() {
    for (FieldLocation fieldLocation : Locate.fieldLocations()) {
      try {
        fieldLocation.access();
      } catch (Exception exception) {
        IntaveLogger.logger().warn("Field " + fieldLocation.key() + "/" + fieldLocation.targetName() + " in class " + fieldLocation.classKey() + " does not exist");
        throw exception;
      }
    }
  }
}

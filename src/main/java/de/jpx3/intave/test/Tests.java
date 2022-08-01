package de.jpx3.intave.test;

public abstract class Tests {
  protected void assertEquals(Object expected, Object actual) {
    if (!expected.equals(actual)) {
      throw new AssertionError("Expected: " + expected + ", actual: " + actual);
    }
  }

  protected void assertTrue(boolean condition) {
    if (!condition) {
      throw new AssertionError("Expected true, actual false");
    }
  }

  protected void assertFalse(boolean condition) {
    if (condition) {
      throw new AssertionError("Expected false, actual true");
    }
  }

  protected void assertNotNull(Object object) {
    if (object == null) {
      throw new AssertionError("Expected non-null object");
    }
  }

  protected void assertNull(Object object) {
    if (object != null) {
      throw new AssertionError("Expected null object");
    }
  }

  protected void assertSame(Object expected, Object actual) {
    if (expected != actual) {
      throw new AssertionError("Expected: " + expected + ", actual: " + actual);
    }
  }

  protected void assertNotSame(Object expected, Object actual) {
    if (expected == actual) {
      throw new AssertionError("Expected: " + expected + ", actual: " + actual);
    }
  }

  protected void assertNotEquals(Object expected, Object actual) {
    if (expected.equals(actual)) {
      throw new AssertionError("Expected: " + expected + ", actual: " + actual);
    }
  }
}

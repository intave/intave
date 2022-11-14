package de.jpx3.intave.test;

import java.util.Set;

public abstract class Tests {
  private final String testCode;

  public Tests(String testCode) {
    this.testCode = testCode;
  }

  public String testCode() {
    return testCode;
  }

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

  protected <E> void assertContains(Set<E> set, E element) {
    if (!set.contains(element)) {
      throw new AssertionError("Expected " + set + " to contain " + element);
    }
  }

  protected <E> void assertDoesNotContain(Set<E> set, E element) {
    if (set.contains(element)) {
      throw new AssertionError("Expected " + set + " to not contain " + element);
    }
  }

  protected void fail() {
    throw new AssertionError("Expected failure");
  }

  protected void fail(String message) {
    throw new AssertionError(message);
  }

  @Override
  protected final void finalize() {
    TestService.testClearedByGC(testCode);
  }
}

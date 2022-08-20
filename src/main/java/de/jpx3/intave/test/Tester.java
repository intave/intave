package de.jpx3.intave.test;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class Tester implements Runnable {
  private Class<?> testClass;

  private Method beforeMethod;
  private Method afterMethod;

  private final List<Method> testMethods = new ArrayList<>();

  public Tester(Class<? extends Tests> testClass) {
    this.testClass = testClass;
  }

  @Override
  public void run() {
    prepare();
    runTests();
    reset();
  }

  private void prepare() {
    Method[] methods = testClass.getMethods();
    for (Method method : methods) {
      if (method.getAnnotation(Test.class) != null) {
        testMethods.add(method);
      }
      if (method.getAnnotation(Before.class) != null) {
        if (beforeMethod != null) {
          throw new RuntimeException("Only one @Before method allowed");
        }
        beforeMethod = method;
      }
      if (method.getAnnotation(After.class) != null) {
        if (afterMethod != null) {
          throw new RuntimeException("Only one @After method allowed");
        }
        afterMethod = method;
      }
    }
  }

  private void runTests() {
    for (Method method : testMethods) {
      runTest(method);
    }
  }

  private void runTest(Method testMethod) {
    Test annotation = testMethod.getAnnotation(Test.class);
    String testCode = annotation.testCode();

    Tests test;
    try {
      test = (Tests) testClass.newInstance();
    } catch (Exception exception) {
      throw new RuntimeException("Failed to instantiate test", exception);
    }
    String fullTestName = test.testCode() + "/" + testCode;
    try {
      if (beforeMethod != null) {
        beforeMethod.invoke(test);
      }
    } catch (Throwable t) {
      throw new RuntimeException("Failed to run @Before method for "+ fullTestName, t);
    }
    try {
      if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
        IntaveLogger.logger().info("Running test: " + testMethod.getName());
      }
      testMethod.invoke(test);
    } catch (Throwable throwable) {
      Severity severity = annotation.severity();
      String message = "Test " + fullTestName + " failed";
      if (IntaveControl.DEBUG_OUTPUT_FOR_TESTS) {
        throwable.printStackTrace();
        while (throwable.getCause() != null) {
          throwable = throwable.getCause();
        }
        message += " from a " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
      } else {
        message += "";
      }
      IntaveLogger.logger().info(message);
      if (severity.mustInterrupt()) {
        throw new RuntimeException(message, throwable);
      }
    } finally {
      if (afterMethod != null) {
        try {
          afterMethod.invoke(test);
        } catch (Exception exception) {
          exception.printStackTrace();
        }
      }
    }
  }

  private void reset() {
    beforeMethod = null;
    afterMethod = null;
    testMethods.clear();
    testClass = null;
  }
}

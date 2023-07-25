## Code style guide

Most of the time, we follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

### [4.1.3](https://google.github.io/styleguide/javaguide.html#s4.1.3-braces-empty-blocks) Empty catch blocks
```java
void method() {
  try {
    doSomething();
  } catch (Exception e) {}
}
```
are allowed, but discouraged. If you want to ignore an exception, you should document why you are doing so:

```java
void method() {
  try {
    doSomething();
  } catch (Exception e) {
    // We don't care if this fails, so we ignore the exception
  }
}
```

### [4.8.2.2](https://google.github.io/styleguide/javaguide.html#s4.8.2-variable-declarations) Declared when needed
We extend the local variable concept to global variables.<br>
When doing so improves readability, fields should be declared as close to their usage as possible, if they have no meaning outside the method.
Example:

```java
class MyClass {
  private int myField;
  private int myOtherField;
  private int myThirdField;
  private int myFourthField;
  private int myFifthField;
  private int mySixthField;
  private int mySeventhField;

  // ...

  private final Queue<String> processQueue = new LinkedList<>();

  void method3() {
    processQueue.add("Hello");
  }

  String method4() {
    return processQueue.poll();
  }
}
```
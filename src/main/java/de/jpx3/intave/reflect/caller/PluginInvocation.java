package de.jpx3.intave.reflect.caller;

public final class PluginInvocation {
  private final String pluginName;
  private final String className;
  private final String methodName;

  public PluginInvocation(String pluginName, String className, String methodName) {
    this.pluginName = pluginName;
    this.className = className;
    this.methodName = methodName;
  }

  public String pluginName() {
    return pluginName;
  }

  public String className() {
    return className;
  }

  public String methodName() {
    return methodName;
  }

  @Override
  public String toString() {
    return "PluginInvokationInfo{" +
      "pluginName='" + pluginName + '\'' +
      ", className='" + className + '\'' +
      ", methodName='" + methodName + '\'' +
      '}';
  }
}

package de.jpx3.intave.module.nayoro;

public final class DebugForwardEventSink extends EventSink {
  private final EventSink sink;
  private int count = 1;

  public DebugForwardEventSink() {
    this.sink = null;
  }

  public DebugForwardEventSink(EventSink sink) {
    this.sink = sink;
  }

  @Override
  public void visitAny(Event event) {
    System.out.println("#" + count++ + ": " + event);
    if (sink != null) {
      sink.visitSelect(event);
    }
  }
}

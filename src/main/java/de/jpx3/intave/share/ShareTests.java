package de.jpx3.intave.share;

import de.jpx3.intave.test.IntegrationTests;
import de.jpx3.intave.test.Severity;
import de.jpx3.intave.test.Test;

public final class ShareTests extends IntegrationTests {
  public ShareTests() {
    super("SHR");
  }

  @Test(severity = Severity.ERROR)
  public void testHistoryWindow() {
    HistoryWindow<Integer> historyWindow = new HistoryWindow<>(10);
    for (int i = 0; i <= 40; i++) {
      historyWindow.add(i);
    }
    for (int i = 0; i < 10; i++) {
      if (historyWindow.back(i) != 40 - i) {
        fail(historyWindow.back(i) + " != " + (40 - i));
      }
    }
  }
}

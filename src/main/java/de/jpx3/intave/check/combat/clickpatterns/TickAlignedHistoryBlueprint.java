package de.jpx3.intave.check.combat.clickpatterns;

import de.jpx3.intave.check.Blueprint;
import de.jpx3.intave.check.combat.ClickPatterns;

public class TickAlignedHistoryBlueprint<E extends TickAlignedMeta> extends Blueprint<ClickPatterns, TickAlignedMeta, E> {
  public TickAlignedHistoryBlueprint(ClickPatterns parentCheck, Class<? extends E> metaClass) {
    super(parentCheck, metaClass);
  }

  public enum TickAction {
    NOTHING(' '),
    CLICK('C'),
    ATTACK('A'),
    PLACE('P'),
    ;
    private final char representation;

    TickAction(char representation) {
      this.representation = representation;
    }

    public char repChar() {
      return representation;
    }
  }
}


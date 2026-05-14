package de.jpx3.intave.check.other;

import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.other.multiactions.*;

public final class MultiActions extends Check {

    public MultiActions() {
        super("MultiActions", "multiactions");

        appendCheckParts(
                new AttackEntity(this),
                new BlockDig(this),
                new ArmAnimation(this),
                new CloseWindow(this)
        );
    }
}
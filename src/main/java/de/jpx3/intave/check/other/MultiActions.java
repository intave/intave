package de.jpx3.intave.check.other;

import de.jpx3.intave.check.Check;
import de.jpx3.intave.check.other.multiactions.ArmAnimation;
import de.jpx3.intave.check.other.multiactions.AttackEntity;
import de.jpx3.intave.check.other.multiactions.BlockDig;

public final class MultiActions extends Check {

    public MultiActions() {
        super("MultiActions", "multiactions");

        appendCheckParts(
                new AttackEntity(this),
                new BlockDig(this),
                new ArmAnimation(this)
        );
    }
}
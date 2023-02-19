package de.jpx3.intave;


import de.jpx3.intave.annotate.NameIntrinsicallyImportant;
import de.jpx3.intave.library.asm.Frame;

@NameIntrinsicallyImportant
public final class IntaveRefdata {
  static {
    Frame.class.hashCode();
  }
}

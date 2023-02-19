package de.jpx3.intave.agent;

import de.jpx3.intave.library.asm.MethodVisitor;

public interface MethodTransformer {
  MethodVisitor replace(
    MethodVisitor visitor,
    int access,
    String name,
    String descriptor
  );
}

// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
package de.jpx3.intave.library.asm.util;

import de.jpx3.intave.library.asm.*;

/**
 * A {@link MethodVisitor} that prints the methods it visits with a {@link Printer}.
 *
 * @author Eric Bruneton
 */
public final class TraceMethodVisitor extends MethodVisitor {

  /**
   * The printer to convert the visited method into text.
   */
  // DontCheck(MemberName): can't be renamed (for backward binary compatibility).
  public final Printer p;

  /**
   * Constructs a new {@link TraceMethodVisitor}.
   *
   * @param printer the printer to convert the visited method into text.
   */
  public TraceMethodVisitor(Printer printer) {
    this(null, printer);
  }

  /**
   * Constructs a new {@link TraceMethodVisitor}.
   *
   * @param methodVisitor the method visitor to which to delegate calls. May be {@literal null}.
   * @param printer       the printer to convert the visited method into text.
   */
  public TraceMethodVisitor(MethodVisitor methodVisitor, Printer printer) {
    super(/* latest api = */ Opcodes.ASM7, methodVisitor);
    this.p = printer;
  }

  @Override
  public void visitParameter(String name, int access) {
    p.visitParameter(name, access);
    super.visitParameter(name, access);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    Printer annotationPrinter = p.visitMethodAnnotation(descriptor, visible);
    return new TraceAnnotationVisitor(
      super.visitAnnotation(descriptor, visible), annotationPrinter);
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(
    int typeRef, TypePath typePath, String descriptor, boolean visible) {
    Printer annotationPrinter = p.visitMethodTypeAnnotation(typeRef, typePath, descriptor, visible);
    return new TraceAnnotationVisitor(
      super.visitTypeAnnotation(typeRef, typePath, descriptor, visible), annotationPrinter);
  }

  @Override
  public void visitAttribute(Attribute attribute) {
    p.visitMethodAttribute(attribute);
    super.visitAttribute(attribute);
  }

  @Override
  public AnnotationVisitor visitAnnotationDefault() {
    Printer annotationPrinter = p.visitAnnotationDefault();
    return new TraceAnnotationVisitor(super.visitAnnotationDefault(), annotationPrinter);
  }

  @Override
  public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
    p.visitAnnotableParameterCount(parameterCount, visible);
    super.visitAnnotableParameterCount(parameterCount, visible);
  }

  @Override
  public AnnotationVisitor visitParameterAnnotation(
    int parameter, String descriptor, boolean visible) {
    Printer annotationPrinter = p.visitParameterAnnotation(parameter, descriptor, visible);
    return new TraceAnnotationVisitor(
      super.visitParameterAnnotation(parameter, descriptor, visible), annotationPrinter);
  }

  @Override
  public void visitCode() {
    p.visitCode();
    super.visitCode();
  }

  @Override
  public void visitFrame(
    int type,
    int numLocal,
    Object[] local,
    int numStack,
    Object[] stack) {
    p.visitFrame(type, numLocal, local, numStack, stack);
    super.visitFrame(type, numLocal, local, numStack, stack);
  }

  @Override
  public void visitInsn(int opcode) {
    p.visitInsn(opcode);
    super.visitInsn(opcode);
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    p.visitIntInsn(opcode, operand);
    super.visitIntInsn(opcode, operand);
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    p.visitVarInsn(opcode, var);
    super.visitVarInsn(opcode, var);
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    p.visitTypeInsn(opcode, type);
    super.visitTypeInsn(opcode, type);
  }

  @Override
  public void visitFieldInsn(
    int opcode, String owner, String name, String descriptor) {
    p.visitFieldInsn(opcode, owner, name, descriptor);
    super.visitFieldInsn(opcode, owner, name, descriptor);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void visitMethodInsn(
    int opcode,
    String owner,
    String name,
    String descriptor,
    boolean isInterface) {
    // Call the method that p is supposed to implement, depending on its api version.
    if (p.api < Opcodes.ASM5) {
      if (isInterface != (opcode == Opcodes.INVOKEINTERFACE)) {
        throw new IllegalArgumentException("INVOKESPECIAL/STATIC on interfaces require ASM5");
      }
      // If p is an ASMifier (resp. Textifier), or a subclass that does not override the old
      // visitMethodInsn method, the default implementation in Printer will redirect this to the
      // new method in ASMifier (resp. Textifier). In all other cases, p overrides the old method
      // and this call executes it.
      p.visitMethodInsn(opcode, owner, name, descriptor);
    } else {
      p.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
    if (mv != null) {
      mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
  }

  @Override
  public void visitInvokeDynamicInsn(
    String name,
    String descriptor,
    Handle bootstrapMethodHandle,
    Object... bootstrapMethodArguments) {
    p.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    p.visitJumpInsn(opcode, label);
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitLabel(Label label) {
    p.visitLabel(label);
    super.visitLabel(label);
  }

  @Override
  public void visitLdcInsn(Object value) {
    p.visitLdcInsn(value);
    super.visitLdcInsn(value);
  }

  @Override
  public void visitIincInsn(int var, int increment) {
    p.visitIincInsn(var, increment);
    super.visitIincInsn(var, increment);
  }

  @Override
  public void visitTableSwitchInsn(
    int min, int max, Label dflt, Label... labels) {
    p.visitTableSwitchInsn(min, max, dflt, labels);
    super.visitTableSwitchInsn(min, max, dflt, labels);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    p.visitLookupSwitchInsn(dflt, keys, labels);
    super.visitLookupSwitchInsn(dflt, keys, labels);
  }

  @Override
  public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
    p.visitMultiANewArrayInsn(descriptor, numDimensions);
    super.visitMultiANewArrayInsn(descriptor, numDimensions);
  }

  @Override
  public AnnotationVisitor visitInsnAnnotation(
    int typeRef, TypePath typePath, String descriptor, boolean visible) {
    Printer annotationPrinter = p.visitInsnAnnotation(typeRef, typePath, descriptor, visible);
    return new TraceAnnotationVisitor(
      super.visitInsnAnnotation(typeRef, typePath, descriptor, visible), annotationPrinter);
  }

  @Override
  public void visitTryCatchBlock(
    Label start, Label end, Label handler, String type) {
    p.visitTryCatchBlock(start, end, handler, type);
    super.visitTryCatchBlock(start, end, handler, type);
  }

  @Override
  public AnnotationVisitor visitTryCatchAnnotation(
    int typeRef, TypePath typePath, String descriptor, boolean visible) {
    Printer annotationPrinter = p.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible);
    return new TraceAnnotationVisitor(
      super.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible), annotationPrinter);
  }

  @Override
  public void visitLocalVariable(
    String name,
    String descriptor,
    String signature,
    Label start,
    Label end,
    int index) {
    p.visitLocalVariable(name, descriptor, signature, start, end, index);
    super.visitLocalVariable(name, descriptor, signature, start, end, index);
  }

  @Override
  public AnnotationVisitor visitLocalVariableAnnotation(
    int typeRef,
    TypePath typePath,
    Label[] start,
    Label[] end,
    int[] index,
    String descriptor,
    boolean visible) {
    Printer annotationPrinter =
      p.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible);
    return new TraceAnnotationVisitor(
      super.visitLocalVariableAnnotation(
        typeRef, typePath, start, end, index, descriptor, visible),
      annotationPrinter);
  }

  @Override
  public void visitLineNumber(int line, Label start) {
    p.visitLineNumber(line, start);
    super.visitLineNumber(line, start);
  }

  @Override
  public void visitMaxs(int maxStack, int maxLocals) {
    p.visitMaxs(maxStack, maxLocals);
    super.visitMaxs(maxStack, maxLocals);
  }

  @Override
  public void visitEnd() {
    p.visitMethodEnd();
    super.visitEnd();
  }
}

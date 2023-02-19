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

package de.jpx3.intave.library.asm.commons;

import de.jpx3.intave.library.asm.*;

/**
 * A {@link MethodVisitor} that remaps types with a {@link Remapper}.
 *
 * @author Eugene Kuleshov
 */
public class MethodRemapper extends MethodVisitor {

  /**
   * The remapper used to remap the types in the visited field.
   */
  protected final Remapper remapper;

  /**
   * Constructs a new {@link MethodRemapper}. <i>Subclasses must not use this constructor</i>.
   * Instead, they must use the {@link #MethodRemapper(int, MethodVisitor, Remapper)} version.
   *
   * @param methodVisitor the method visitor this remapper must deleted to.
   * @param remapper      the remapper to use to remap the types in the visited method.
   */
  public MethodRemapper(MethodVisitor methodVisitor, Remapper remapper) {
    this(/* latest api = */ Opcodes.ASM7, methodVisitor, remapper);
  }

  /**
   * Constructs a new {@link MethodRemapper}.
   *
   * @param api           the ASM API version supported by this remapper. Must be one of {@link
   *                      Opcodes#ASM4}, {@link Opcodes#ASM5} or {@link
   *                      Opcodes#ASM6}.
   * @param methodVisitor the method visitor this remapper must deleted to.
   * @param remapper      the remapper to use to remap the types in the visited method.
   */
  protected MethodRemapper(
    int api, MethodVisitor methodVisitor, Remapper remapper) {
    super(api, methodVisitor);
    this.remapper = remapper;
  }

  @Override
  public AnnotationVisitor visitAnnotationDefault() {
    AnnotationVisitor annotationVisitor = super.visitAnnotationDefault();
    return annotationVisitor == null
      ? annotationVisitor
      : createAnnotationRemapper(annotationVisitor);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    AnnotationVisitor annotationVisitor =
      super.visitAnnotation(remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
      ? annotationVisitor
      : createAnnotationRemapper(annotationVisitor);
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(
    int typeRef, TypePath typePath, String descriptor, boolean visible) {
    AnnotationVisitor annotationVisitor =
      super.visitTypeAnnotation(typeRef, typePath, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
      ? annotationVisitor
      : createAnnotationRemapper(annotationVisitor);
  }

  @Override
  public AnnotationVisitor visitParameterAnnotation(
    int parameter, String descriptor, boolean visible) {
    AnnotationVisitor annotationVisitor =
      super.visitParameterAnnotation(parameter, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
      ? annotationVisitor
      : createAnnotationRemapper(annotationVisitor);
  }

  @Override
  public void visitFrame(
    int type,
    int numLocal,
    Object[] local,
    int numStack,
    Object[] stack) {
    super.visitFrame(
      type,
      numLocal,
      remapFrameTypes(numLocal, local),
      numStack,
      remapFrameTypes(numStack, stack));
  }

  private Object[] remapFrameTypes(int numTypes, Object[] frameTypes) {
    if (frameTypes == null) {
      return frameTypes;
    }
    Object[] remappedFrameTypes = null;
    for (int i = 0; i < numTypes; ++i) {
      if (frameTypes[i] instanceof String) {
        if (remappedFrameTypes == null) {
          remappedFrameTypes = new Object[numTypes];
          System.arraycopy(frameTypes, 0, remappedFrameTypes, 0, numTypes);
        }
        remappedFrameTypes[i] = remapper.mapType((String) frameTypes[i]);
      }
    }
    return remappedFrameTypes == null ? frameTypes : remappedFrameTypes;
  }

  @Override
  public void visitFieldInsn(
    int opcode, String owner, String name, String descriptor) {
    super.visitFieldInsn(
      opcode,
      remapper.mapType(owner),
      remapper.mapFieldName(owner, name, descriptor),
      remapper.mapDesc(descriptor));
  }

  @Override
  public void visitMethodInsn(
    int opcodeAndSource,
    String owner,
    String name,
    String descriptor,
    boolean isInterface) {
    if (api < Opcodes.ASM5 && (opcodeAndSource & Opcodes.SOURCE_DEPRECATED) == 0) {
      // Redirect the call to the deprecated version of this method.
      super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface);
      return;
    }
    super.visitMethodInsn(
      opcodeAndSource,
      remapper.mapType(owner),
      remapper.mapMethodName(owner, name, descriptor),
      remapper.mapMethodDesc(descriptor),
      isInterface);
  }

  @Override
  public void visitInvokeDynamicInsn(
    String name,
    String descriptor,
    Handle bootstrapMethodHandle,
    Object... bootstrapMethodArguments) {
    Object[] remappedBootstrapMethodArguments = new Object[bootstrapMethodArguments.length];
    for (int i = 0; i < bootstrapMethodArguments.length; ++i) {
      remappedBootstrapMethodArguments[i] = remapper.mapValue(bootstrapMethodArguments[i]);
    }
    super.visitInvokeDynamicInsn(
      remapper.mapInvokeDynamicMethodName(name, descriptor),
      remapper.mapMethodDesc(descriptor),
      (Handle) remapper.mapValue(bootstrapMethodHandle),
      remappedBootstrapMethodArguments);
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    super.visitTypeInsn(opcode, remapper.mapType(type));
  }

  @Override
  public void visitLdcInsn(Object value) {
    super.visitLdcInsn(remapper.mapValue(value));
  }

  @Override
  public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
    super.visitMultiANewArrayInsn(remapper.mapDesc(descriptor), numDimensions);
  }

  @Override
  public AnnotationVisitor visitInsnAnnotation(
    int typeRef, TypePath typePath, String descriptor, boolean visible) {
    AnnotationVisitor annotationVisitor =
      super.visitInsnAnnotation(typeRef, typePath, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
      ? annotationVisitor
      : createAnnotationRemapper(annotationVisitor);
  }

  @Override
  public void visitTryCatchBlock(
    Label start, Label end, Label handler, String type) {
    super.visitTryCatchBlock(start, end, handler, type == null ? null : remapper.mapType(type));
  }

  @Override
  public AnnotationVisitor visitTryCatchAnnotation(
    int typeRef, TypePath typePath, String descriptor, boolean visible) {
    AnnotationVisitor annotationVisitor =
      super.visitTryCatchAnnotation(typeRef, typePath, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
      ? annotationVisitor
      : createAnnotationRemapper(annotationVisitor);
  }

  @Override
  public void visitLocalVariable(
    String name,
    String descriptor,
    String signature,
    Label start,
    Label end,
    int index) {
    super.visitLocalVariable(
      name,
      remapper.mapDesc(descriptor),
      remapper.mapSignature(signature, true),
      start,
      end,
      index);
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
    AnnotationVisitor annotationVisitor =
      super.visitLocalVariableAnnotation(
        typeRef, typePath, start, end, index, remapper.mapDesc(descriptor), visible);
    return annotationVisitor == null
      ? annotationVisitor
      : createAnnotationRemapper(annotationVisitor);
  }

  /**
   * Constructs a new remapper for annotations. The default implementation of this method returns a
   * new {@link AnnotationRemapper}.
   *
   * @param annotationVisitor the AnnotationVisitor the remapper must delegate to.
   * @return the newly created remapper.
   */
  protected AnnotationVisitor createAnnotationRemapper(AnnotationVisitor annotationVisitor) {
    return new AnnotationRemapper(api, annotationVisitor, remapper);
  }
}

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
 * A {@link MethodVisitor} that approximates the size of the methods it visits.
 *
 * @author Eugene Kuleshov
 */
public class CodeSizeEvaluator extends MethodVisitor implements Opcodes {

  /**
   * The minimum size in bytes of the visited method.
   */
  private int minSize;

  /**
   * The maximum size in bytes of the visited method.
   */
  private int maxSize;

  public CodeSizeEvaluator(MethodVisitor methodVisitor) {
    this(/* latest api = */ Opcodes.ASM7, methodVisitor);
  }

  protected CodeSizeEvaluator(int api, MethodVisitor methodVisitor) {
    super(api, methodVisitor);
  }

  public int getMinSize() {
    return this.minSize;
  }

  public int getMaxSize() {
    return this.maxSize;
  }

  @Override
  public void visitInsn(int opcode) {
    minSize += 1;
    maxSize += 1;
    super.visitInsn(opcode);
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    if (opcode == SIPUSH) {
      minSize += 3;
      maxSize += 3;
    } else {
      minSize += 2;
      maxSize += 2;
    }
    super.visitIntInsn(opcode, operand);
  }

  @Override
  public void visitVarInsn(int opcode, int var) {
    if (var < 4 && opcode != RET) {
      minSize += 1;
      maxSize += 1;
    } else if (var >= 256) {
      minSize += 4;
      maxSize += 4;
    } else {
      minSize += 2;
      maxSize += 2;
    }
    super.visitVarInsn(opcode, var);
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    minSize += 3;
    maxSize += 3;
    super.visitTypeInsn(opcode, type);
  }

  @Override
  public void visitFieldInsn(
    int opcode, String owner, String name, String descriptor) {
    minSize += 3;
    maxSize += 3;
    super.visitFieldInsn(opcode, owner, name, descriptor);
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
    int opcode = opcodeAndSource & ~Opcodes.SOURCE_MASK;

    if (opcode == INVOKEINTERFACE) {
      minSize += 5;
      maxSize += 5;
    } else {
      minSize += 3;
      maxSize += 3;
    }
    super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface);
  }

  @Override
  public void visitInvokeDynamicInsn(
    String name,
    String descriptor,
    Handle bootstrapMethodHandle,
    Object... bootstrapMethodArguments) {
    minSize += 5;
    maxSize += 5;
    super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    minSize += 3;
    if (opcode == GOTO || opcode == JSR) {
      maxSize += 5;
    } else {
      maxSize += 8;
    }
    super.visitJumpInsn(opcode, label);
  }

  @Override
  public void visitLdcInsn(Object value) {
    if (value instanceof Long
      || value instanceof Double
      || (value instanceof ConstantDynamic && ((ConstantDynamic) value).getSize() == 2)) {
      minSize += 3;
      maxSize += 3;
    } else {
      minSize += 2;
      maxSize += 3;
    }
    super.visitLdcInsn(value);
  }

  @Override
  public void visitIincInsn(int var, int increment) {
    if (var > 255 || increment > 127 || increment < -128) {
      minSize += 6;
      maxSize += 6;
    } else {
      minSize += 3;
      maxSize += 3;
    }
    super.visitIincInsn(var, increment);
  }

  @Override
  public void visitTableSwitchInsn(
    int min, int max, Label dflt, Label... labels) {
    minSize += 13 + labels.length * 4;
    maxSize += 16 + labels.length * 4;
    super.visitTableSwitchInsn(min, max, dflt, labels);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    minSize += 9 + keys.length * 8;
    maxSize += 12 + keys.length * 8;
    super.visitLookupSwitchInsn(dflt, keys, labels);
  }

  @Override
  public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
    minSize += 4;
    maxSize += 4;
    super.visitMultiANewArrayInsn(descriptor, numDimensions);
  }
}

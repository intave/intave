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
 * A {@link RecordComponentVisitor} that checks that its methods are properly used.
 *
 * @author Eric Bruneton
 * @author Remi Forax
 */
public class CheckRecordComponentAdapter extends RecordComponentVisitor {

  /**
   * Whether the {@link #visitEndExperimental()} method has been called.
   */
  private boolean visitEndCalled;

  /**
   * Constructs a new {@link CheckRecordComponentAdapter}. <i>Subclasses must not use this
   * constructor</i>. Instead, they must use the {@link #CheckRecordComponentAdapter(int,
   * RecordComponentVisitor)} version.
   *
   * @param recordComponentVisitor the record component visitor to which this adapter must delegate
   *                               calls.
   * @throws IllegalStateException If a subclass calls this constructor.
   */
  public CheckRecordComponentAdapter(RecordComponentVisitor recordComponentVisitor) {
    // TODO: add 'latest api =' comment when no longer experimental.
    this(Opcodes.ASM8_EXPERIMENTAL, recordComponentVisitor);
    if (getClass() != CheckRecordComponentAdapter.class) {
      throw new IllegalStateException();
    }
  }

  /**
   * Constructs a new {@link CheckRecordComponentAdapter}.
   *
   * @param api                    the ASM API version implemented by this visitor. Must be {@link
   *                               Opcodes#ASM8_EXPERIMENTAL}.
   * @param recordComponentVisitor the record component visitor to which this adapter must delegate
   *                               calls.
   */
  protected CheckRecordComponentAdapter(
    int api, RecordComponentVisitor recordComponentVisitor) {
    super(api, recordComponentVisitor);
  }

  @Override
  public AnnotationVisitor visitAnnotationExperimental(
    String descriptor, boolean visible) {
    checkVisitEndNotCalled();
    // Annotations can only appear in V1_5 or more classes.
    CheckMethodAdapter.checkDescriptor(Opcodes.V1_5, descriptor, false);
    return new CheckAnnotationAdapter(super.visitAnnotationExperimental(descriptor, visible));
  }

  @Override
  public AnnotationVisitor visitTypeAnnotationExperimental(
    int typeRef, TypePath typePath, String descriptor, boolean visible) {
    checkVisitEndNotCalled();
    int sort = new TypeReference(typeRef).getSort();
    if (sort != TypeReference.FIELD) {
      throw new IllegalArgumentException(
        "Invalid type reference sort 0x" + Integer.toHexString(sort));
    }
    CheckClassAdapter.checkTypeRef(typeRef);
    CheckMethodAdapter.checkDescriptor(Opcodes.V1_5, descriptor, false);
    return new CheckAnnotationAdapter(
      super.visitTypeAnnotationExperimental(typeRef, typePath, descriptor, visible));
  }

  @Override
  public void visitAttributeExperimental(Attribute attribute) {
    checkVisitEndNotCalled();
    if (attribute == null) {
      throw new IllegalArgumentException("Invalid attribute (must not be null)");
    }
    super.visitAttributeExperimental(attribute);
  }

  @Override
  public void visitEndExperimental() {
    checkVisitEndNotCalled();
    visitEndCalled = true;
    super.visitEndExperimental();
  }

  private void checkVisitEndNotCalled() {
    if (visitEndCalled) {
      throw new IllegalStateException("Cannot call a visit method after visitEnd has been called");
    }
  }
}

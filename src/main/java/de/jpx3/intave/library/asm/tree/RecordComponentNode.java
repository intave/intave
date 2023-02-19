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
package de.jpx3.intave.library.asm.tree;

import de.jpx3.intave.library.asm.*;

import java.util.List;

/**
 * A node that represents a record component.
 *
 * @author Remi Forax
 * @deprecated this API is experimental.
 */
@Deprecated
public class RecordComponentNode extends RecordComponentVisitor {

  /**
   * The record component access flags (see {@link Opcodes}). The only valid value
   * is {@link Opcodes#ACC_DEPRECATED}.
   *
   * @deprecated this API is experimental.
   */
  public int accessExperimental;

  /**
   * The record component name.
   *
   * @deprecated this API is experimental.
   */
  public String nameExperimental;

  /**
   * The record component descriptor (see {@link Type}).
   *
   * @deprecated this API is experimental.
   */
  public String descriptorExperimental;

  /**
   * The record component signature. May be {@literal null}.
   *
   * @deprecated this API is experimental.
   */
  public String signatureExperimental;

  /**
   * The runtime visible annotations of this record component. May be {@literal null}.
   *
   * @deprecated this API is experimental.
   */
  public List<AnnotationNode> visibleAnnotationsExperimental;

  /**
   * The runtime invisible annotations of this record component. May be {@literal null}.
   *
   * @deprecated this API is experimental.
   */
  public List<AnnotationNode> invisibleAnnotationsExperimental;

  /**
   * The runtime visible type annotations of this record component. May be {@literal null}.
   *
   * @deprecated this API is experimental.
   */
  public List<TypeAnnotationNode> visibleTypeAnnotationsExperimental;

  /**
   * The runtime invisible type annotations of this record component. May be {@literal null}.
   *
   * @deprecated this API is experimental.
   */
  public List<TypeAnnotationNode> invisibleTypeAnnotationsExperimental;

  /**
   * The non standard attributes of this record component. * May be {@literal null}.
   *
   * @deprecated this API is experimental.
   */
  public List<Attribute> attrsExperimental;

  /**
   * Constructs a new {@link RecordComponentNode}. <i>Subclasses must not use this constructor</i>.
   * Instead, they must use the {@link #RecordComponentNode(int, int, String, String, String)}
   * version.
   *
   * @param access     the record component access flags (see {@link Opcodes}). The
   *                   only valid value is {@link Opcodes#ACC_DEPRECATED}.
   * @param name       the record component name.
   * @param descriptor the record component descriptor (see {@link Type}).
   * @param signature  the record component signature.
   * @throws IllegalStateException If a subclass calls this constructor.
   * @deprecated this API is experimental.
   */
  @Deprecated
  public RecordComponentNode(
    int access, String name, String descriptor, String signature) {
    this(/* latest api = */ Opcodes.ASM7, access, name, descriptor, signature);
    if (getClass() != RecordComponentNode.class) {
      throw new IllegalStateException();
    }
  }

  /**
   * Constructs a new {@link RecordComponentNode}.
   *
   * @param api        the ASM API version implemented by this visitor. Must be {@link
   *                   Opcodes#ASM8_EXPERIMENTAL}.
   * @param access     the record component access flags (see {@link Opcodes}). The
   *                   only valid value is {@link Opcodes#ACC_DEPRECATED}.
   * @param name       the record component name.
   * @param descriptor the record component descriptor (see {@link Type}).
   * @param signature  the record component signature.
   * @deprecated this API is experimental.
   */
  @Deprecated
  public RecordComponentNode(
    int api,
    int access,
    String name,
    String descriptor,
    String signature) {
    super(api);
    this.accessExperimental = access;
    this.nameExperimental = name;
    this.descriptorExperimental = descriptor;
    this.signatureExperimental = signature;
  }

  // -----------------------------------------------------------------------------------------------
  // Implementation of the FieldVisitor abstract class
  // -----------------------------------------------------------------------------------------------

  @Override
  public AnnotationVisitor visitAnnotationExperimental(
    String descriptor, boolean visible) {
    AnnotationNode annotation = new AnnotationNode(descriptor);
    if (visible) {
      visibleAnnotationsExperimental = Util.add(visibleAnnotationsExperimental, annotation);
    } else {
      invisibleAnnotationsExperimental = Util.add(invisibleAnnotationsExperimental, annotation);
    }
    return annotation;
  }

  @Override
  public AnnotationVisitor visitTypeAnnotationExperimental(
    int typeRef, TypePath typePath, String descriptor, boolean visible) {
    TypeAnnotationNode typeAnnotation = new TypeAnnotationNode(typeRef, typePath, descriptor);
    if (visible) {
      visibleTypeAnnotationsExperimental =
        Util.add(visibleTypeAnnotationsExperimental, typeAnnotation);
    } else {
      invisibleTypeAnnotationsExperimental =
        Util.add(invisibleTypeAnnotationsExperimental, typeAnnotation);
    }
    return typeAnnotation;
  }

  @Override
  public void visitAttributeExperimental(Attribute attribute) {
    attrsExperimental = Util.add(attrsExperimental, attribute);
  }

  @Override
  public void visitEndExperimental() {
    // Nothing to do.
  }

  // -----------------------------------------------------------------------------------------------
  // Accept methods
  // -----------------------------------------------------------------------------------------------

  /**
   * Checks that this record component node is compatible with the given ASM API version. This
   * method checks that this node, and all its children recursively, do not contain elements that
   * were introduced in more recent versions of the ASM API than the given version.
   *
   * @param api an ASM API version. Must be {@link Opcodes#ASM8_EXPERIMENTAL}.
   * @deprecated this API is experimental.
   */
  public void checkExperimental(int api) {
    if (api != Opcodes.ASM8_EXPERIMENTAL) {
      throw new UnsupportedClassVersionException();
    }
  }

  /**
   * Makes the given class visitor visit this record component.
   *
   * @param classVisitor a class visitor.
   * @deprecated this API is experimental.
   */
  public void acceptExperimental(ClassVisitor classVisitor) {
    RecordComponentVisitor recordComponentVisitor =
      classVisitor.visitRecordComponentExperimental(
        accessExperimental, nameExperimental, descriptorExperimental, signatureExperimental);
    if (recordComponentVisitor == null) {
      return;
    }
    // Visit the annotations.
    if (visibleAnnotationsExperimental != null) {
      for (int i = 0, n = visibleAnnotationsExperimental.size(); i < n; ++i) {
        AnnotationNode annotation = visibleAnnotationsExperimental.get(i);
        annotation.accept(
          recordComponentVisitor.visitAnnotationExperimental(annotation.desc, true));
      }
    }
    if (invisibleAnnotationsExperimental != null) {
      for (int i = 0, n = invisibleAnnotationsExperimental.size(); i < n; ++i) {
        AnnotationNode annotation = invisibleAnnotationsExperimental.get(i);
        annotation.accept(
          recordComponentVisitor.visitAnnotationExperimental(annotation.desc, false));
      }
    }
    if (visibleTypeAnnotationsExperimental != null) {
      for (int i = 0, n = visibleTypeAnnotationsExperimental.size(); i < n; ++i) {
        TypeAnnotationNode typeAnnotation = visibleTypeAnnotationsExperimental.get(i);
        typeAnnotation.accept(
          recordComponentVisitor.visitTypeAnnotationExperimental(
            typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, true));
      }
    }
    if (invisibleTypeAnnotationsExperimental != null) {
      for (int i = 0, n = invisibleTypeAnnotationsExperimental.size(); i < n; ++i) {
        TypeAnnotationNode typeAnnotation = invisibleTypeAnnotationsExperimental.get(i);
        typeAnnotation.accept(
          recordComponentVisitor.visitTypeAnnotationExperimental(
            typeAnnotation.typeRef, typeAnnotation.typePath, typeAnnotation.desc, false));
      }
    }
    // Visit the non standard attributes.
    if (attrsExperimental != null) {
      for (int i = 0, n = attrsExperimental.size(); i < n; ++i) {
        recordComponentVisitor.visitAttributeExperimental(attrsExperimental.get(i));
      }
    }
    recordComponentVisitor.visitEndExperimental();
  }
}

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

import java.io.PrintWriter;

/**
 * A {@link ClassVisitor} that prints the classes it visits with a {@link Printer}. This class
 * visitor can be used in the middle of a class visitor chain to trace the class that is visited at
 * a given point in this chain. This may be useful for debugging purposes.
 *
 * <p>When used with a {@link Textifier}, the trace printed when visiting the {@code Hello} class is
 * the following:
 *
 * <pre>
 * // class version 49.0 (49) // access flags 0x21 public class Hello {
 *
 * // compiled from: Hello.java
 *
 * // access flags 0x1
 * public &lt;init&gt; ()V
 * ALOAD 0
 * INVOKESPECIAL java/lang/Object &lt;init&gt; ()V
 * RETURN
 * MAXSTACK = 1 MAXLOCALS = 1
 *
 * // access flags 0x9
 * public static main ([Ljava/lang/String;)V
 * GETSTATIC java/lang/System out Ljava/io/PrintStream;
 * LDC &quot;hello&quot;
 * INVOKEVIRTUAL java/io/PrintStream println (Ljava/lang/String;)V
 * RETURN
 * MAXSTACK = 2 MAXLOCALS = 1
 * }
 * </pre>
 *
 * <p>where {@code Hello} is defined by:
 *
 * <pre>
 * public class Hello {
 *
 *   public static void main(String[] args) {
 *     IntaveLogger.logger().globalPrintLn(&quot;hello&quot;);
 *   }
 * }
 * </pre>
 *
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 */
public final class TraceClassVisitor extends ClassVisitor {

  /**
   * The print writer to be used to print the class. May be {@literal null}.
   */
  private final PrintWriter printWriter;

  /**
   * The printer to convert the visited class into text.
   */
  // DontCheck(MemberName): can't be renamed (for backward binary compatibility).
  public final Printer p;

  /**
   * Constructs a new {@link TraceClassVisitor}.
   *
   * @param printWriter the print writer to be used to print the class. May be {@literal null}.
   */
  public TraceClassVisitor(PrintWriter printWriter) {
    this(null, printWriter);
  }

  /**
   * Constructs a new {@link TraceClassVisitor}.
   *
   * @param classVisitor the class visitor to which to delegate calls. May be {@literal null}.
   * @param printWriter  the print writer to be used to print the class. May be {@literal null}.
   */
  public TraceClassVisitor(ClassVisitor classVisitor, PrintWriter printWriter) {
    this(classVisitor, new Textifier(), printWriter);
  }

  /**
   * Constructs a new {@link TraceClassVisitor}.
   *
   * @param classVisitor the class visitor to which to delegate calls. May be {@literal null}.
   * @param printer      the printer to convert the visited class into text.
   * @param printWriter  the print writer to be used to print the class. May be {@literal null}.
   */
  public TraceClassVisitor(
    ClassVisitor classVisitor, Printer printer, PrintWriter printWriter) {
    super(/* latest api = */ Opcodes.ASM8_EXPERIMENTAL, classVisitor);
    this.printWriter = printWriter;
    this.p = printer;
  }

  @Override
  public void visit(
    int version,
    int access,
    String name,
    String signature,
    String superName,
    String[] interfaces) {
    p.visit(version, access, name, signature, superName, interfaces);
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public void visitSource(String file, String debug) {
    p.visitSource(file, debug);
    super.visitSource(file, debug);
  }

  @Override
  public ModuleVisitor visitModule(String name, int flags, String version) {
    Printer modulePrinter = p.visitModule(name, flags, version);
    return new TraceModuleVisitor(super.visitModule(name, flags, version), modulePrinter);
  }

  @Override
  public void visitNestHost(String nestHost) {
    p.visitNestHost(nestHost);
    super.visitNestHost(nestHost);
  }

  @Override
  public void visitOuterClass(String owner, String name, String descriptor) {
    p.visitOuterClass(owner, name, descriptor);
    super.visitOuterClass(owner, name, descriptor);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    Printer annotationPrinter = p.visitClassAnnotation(descriptor, visible);
    return new TraceAnnotationVisitor(
      super.visitAnnotation(descriptor, visible), annotationPrinter);
  }

  @Override
  public AnnotationVisitor visitTypeAnnotation(
    int typeRef, TypePath typePath, String descriptor, boolean visible) {
    Printer annotationPrinter = p.visitClassTypeAnnotation(typeRef, typePath, descriptor, visible);
    return new TraceAnnotationVisitor(
      super.visitTypeAnnotation(typeRef, typePath, descriptor, visible), annotationPrinter);
  }

  @Override
  public void visitAttribute(Attribute attribute) {
    p.visitClassAttribute(attribute);
    super.visitAttribute(attribute);
  }

  @Override
  public void visitNestMember(String nestMember) {
    p.visitNestMember(nestMember);
    super.visitNestMember(nestMember);
  }

  @Override
  public void visitPermittedSubtypeExperimental(String permittedSubtype) {
    p.visitPermittedSubtypeExperimental(permittedSubtype);
    super.visitPermittedSubtypeExperimental(permittedSubtype);
  }

  @Override
  public void visitInnerClass(
    String name, String outerName, String innerName, int access) {
    p.visitInnerClass(name, outerName, innerName, access);
    super.visitInnerClass(name, outerName, innerName, access);
  }

  @Override
  public RecordComponentVisitor visitRecordComponentExperimental(
    int access, String name, String descriptor, String signature) {
    Printer recordComponentPrinter =
      p.visitRecordComponentExperimental(access, name, descriptor, signature);
    return new TraceRecordComponentVisitor(
      super.visitRecordComponentExperimental(access, name, descriptor, signature),
      recordComponentPrinter);
  }

  @Override
  public FieldVisitor visitField(
    int access,
    String name,
    String descriptor,
    String signature,
    Object value) {
    Printer fieldPrinter = p.visitField(access, name, descriptor, signature, value);
    return new TraceFieldVisitor(
      super.visitField(access, name, descriptor, signature, value), fieldPrinter);
  }

  @Override
  public MethodVisitor visitMethod(
    int access,
    String name,
    String descriptor,
    String signature,
    String[] exceptions) {
    Printer methodPrinter = p.visitMethod(access, name, descriptor, signature, exceptions);
    return new TraceMethodVisitor(
      super.visitMethod(access, name, descriptor, signature, exceptions), methodPrinter);
  }

  @Override
  public void visitEnd() {
    p.visitClassEnd();
    if (printWriter != null) {
      p.print(printWriter);
      printWriter.flush();
    }
    super.visitEnd();
  }
}

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

import de.jpx3.intave.library.asm.MethodVisitor;
import de.jpx3.intave.library.asm.Opcodes;
import de.jpx3.intave.library.asm.Type;

import java.util.Map;

/**
 * A node that represents a method instruction. A method instruction is an instruction that invokes
 * a method.
 *
 * @author Eric Bruneton
 */
public class MethodInsnNode extends AbstractInsnNode {

  /**
   * The internal name of the method's owner class (see {@link
   * Type#getInternalName()}).
   *
   * <p>For methods of arrays, e.g., {@code clone()}, the array type descriptor.
   */
  public String owner;

  /**
   * The method's name.
   */
  public String name;

  /**
   * The method's descriptor (see {@link Type}).
   */
  public String desc;

  /**
   * Whether the method's owner class if an interface.
   */
  public boolean itf;

  /**
   * Constructs a new {@link MethodInsnNode}.
   *
   * @param opcode     the opcode of the type instruction to be constructed. This opcode must be
   *                   INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE.
   * @param owner      the internal name of the method's owner class (see {@link
   *                   Type#getInternalName()}).
   * @param name       the method's name.
   * @param descriptor the method's descriptor (see {@link Type}).
   */
  public MethodInsnNode(
    int opcode, String owner, String name, String descriptor) {
    this(opcode, owner, name, descriptor, opcode == Opcodes.INVOKEINTERFACE);
  }

  /**
   * Constructs a new {@link MethodInsnNode}.
   *
   * @param opcode      the opcode of the type instruction to be constructed. This opcode must be
   *                    INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE.
   * @param owner       the internal name of the method's owner class (see {@link
   *                    Type#getInternalName()}).
   * @param name        the method's name.
   * @param descriptor  the method's descriptor (see {@link Type}).
   * @param isInterface if the method's owner class is an interface.
   */
  public MethodInsnNode(
    int opcode,
    String owner,
    String name,
    String descriptor,
    boolean isInterface) {
    super(opcode);
    this.owner = owner;
    this.name = name;
    this.desc = descriptor;
    this.itf = isInterface;
  }

  /**
   * Sets the opcode of this instruction.
   *
   * @param opcode the new instruction opcode. This opcode must be INVOKEVIRTUAL, INVOKESPECIAL,
   *               INVOKESTATIC or INVOKEINTERFACE.
   */
  public void setOpcode(int opcode) {
    this.opcode = opcode;
  }

  @Override
  public int getType() {
    return METHOD_INSN;
  }

  @Override
  public void accept(MethodVisitor methodVisitor) {
    methodVisitor.visitMethodInsn(opcode, owner, name, desc, itf);
    acceptAnnotations(methodVisitor);
  }

  @Override
  public AbstractInsnNode clone(Map<LabelNode, LabelNode> clonedLabels) {
    return new MethodInsnNode(opcode, owner, name, desc, itf).cloneAnnotations(this);
  }

  @Override
  public String toString() {
    return "MethodInsnNode{" +
      "owner='" + owner + '\'' +
      ", name='" + name + '\'' +
      ", desc='" + desc + '\'' +
      ", itf=" + itf +
      '}';
  }
}

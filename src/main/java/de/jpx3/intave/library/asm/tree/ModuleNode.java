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

import de.jpx3.intave.library.asm.ClassVisitor;
import de.jpx3.intave.library.asm.ModuleVisitor;
import de.jpx3.intave.library.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * A node that represents a module declaration.
 *
 * @author Remi Forax
 */
public class ModuleNode extends ModuleVisitor {

  /**
   * The fully qualified name (using dots) of this module.
   */
  public String name;

  /**
   * The module's access flags, among {@code ACC_OPEN}, {@code ACC_SYNTHETIC} and {@code
   * ACC_MANDATED}.
   */
  public int access;

  /**
   * The version of this module. May be {@literal null}.
   */
  public String version;

  /**
   * The internal name of the main class of this module. May be {@literal null}.
   */
  public String mainClass;

  /**
   * The internal name of the packages declared by this module. May be {@literal null}.
   */
  public List<String> packages;

  /**
   * The dependencies of this module. May be {@literal null}.
   */
  public List<ModuleRequireNode> requires;

  /**
   * The packages exported by this module. May be {@literal null}.
   */
  public List<ModuleExportNode> exports;

  /**
   * The packages opened by this module. May be {@literal null}.
   */
  public List<ModuleOpenNode> opens;

  /**
   * The internal names of the services used by this module. May be {@literal null}.
   */
  public List<String> uses;

  /**
   * The services provided by this module. May be {@literal null}.
   */
  public List<ModuleProvideNode> provides;

  /**
   * Constructs a {@link ModuleNode}. <i>Subclasses must not use this constructor</i>. Instead, they
   * must use the {@link #ModuleNode(int, String, int, String, List, List, List, List, List)} version.
   *
   * @param name    the fully qualified name (using dots) of the module.
   * @param access  the module access flags, among {@code ACC_OPEN}, {@code ACC_SYNTHETIC} and {@code
   *                ACC_MANDATED}.
   * @param version the module version, or {@literal null}.
   * @throws IllegalStateException If a subclass calls this constructor.
   */
  public ModuleNode(String name, int access, String version) {
    super(/* latest api = */ Opcodes.ASM7);
    if (getClass() != ModuleNode.class) {
      throw new IllegalStateException();
    }
    this.name = name;
    this.access = access;
    this.version = version;
  }

  // TODO(forax): why is there no 'mainClass' and 'packages' parameters in this constructor?

  /**
   * Constructs a {@link ModuleNode}.
   *
   * @param api      the ASM API version implemented by this visitor. Must be one of {@link Opcodes#ASM6}
   *                 or {@link Opcodes#ASM7}.
   * @param name     the fully qualified name (using dots) of the module.
   * @param access   the module access flags, among {@code ACC_OPEN}, {@code ACC_SYNTHETIC} and {@code
   *                 ACC_MANDATED}.
   * @param version  the module version, or {@literal null}.
   * @param requires The dependencies of this module. May be {@literal null}.
   * @param exports  The packages exported by this module. May be {@literal null}.
   * @param opens    The packages opened by this module. May be {@literal null}.
   * @param uses     The internal names of the services used by this module. May be {@literal null}.
   * @param provides The services provided by this module. May be {@literal null}.
   */
  public ModuleNode(
    int api,
    String name,
    int access,
    String version,
    List<ModuleRequireNode> requires,
    List<ModuleExportNode> exports,
    List<ModuleOpenNode> opens,
    List<String> uses,
    List<ModuleProvideNode> provides) {
    super(api);
    this.name = name;
    this.access = access;
    this.version = version;
    this.requires = requires;
    this.exports = exports;
    this.opens = opens;
    this.uses = uses;
    this.provides = provides;
  }

  @Override
  public void visitMainClass(String mainClass) {
    this.mainClass = mainClass;
  }

  @Override
  public void visitPackage(String packaze) {
    if (packages == null) {
      packages = new ArrayList<>(5);
    }
    packages.add(packaze);
  }

  @Override
  public void visitRequire(String module, int access, String version) {
    if (requires == null) {
      requires = new ArrayList<>(5);
    }
    requires.add(new ModuleRequireNode(module, access, version));
  }

  @Override
  public void visitExport(String packaze, int access, String... modules) {
    if (exports == null) {
      exports = new ArrayList<>(5);
    }
    exports.add(new ModuleExportNode(packaze, access, Util.asArrayList(modules)));
  }

  @Override
  public void visitOpen(String packaze, int access, String... modules) {
    if (opens == null) {
      opens = new ArrayList<>(5);
    }
    opens.add(new ModuleOpenNode(packaze, access, Util.asArrayList(modules)));
  }

  @Override
  public void visitUse(String service) {
    if (uses == null) {
      uses = new ArrayList<>(5);
    }
    uses.add(service);
  }

  @Override
  public void visitProvide(String service, String... providers) {
    if (provides == null) {
      provides = new ArrayList<>(5);
    }
    provides.add(new ModuleProvideNode(service, Util.asArrayList(providers)));
  }

  @Override
  public void visitEnd() {
    // Nothing to do.
  }

  /**
   * Makes the given class visitor visit this module.
   *
   * @param classVisitor a class visitor.
   */
  public void accept(ClassVisitor classVisitor) {
    ModuleVisitor moduleVisitor = classVisitor.visitModule(name, access, version);
    if (moduleVisitor == null) {
      return;
    }
    if (mainClass != null) {
      moduleVisitor.visitMainClass(mainClass);
    }
    if (packages != null) {
      for (int i = 0, n = packages.size(); i < n; i++) {
        moduleVisitor.visitPackage(packages.get(i));
      }
    }
    if (requires != null) {
      for (int i = 0, n = requires.size(); i < n; i++) {
        requires.get(i).accept(moduleVisitor);
      }
    }
    if (exports != null) {
      for (int i = 0, n = exports.size(); i < n; i++) {
        exports.get(i).accept(moduleVisitor);
      }
    }
    if (opens != null) {
      for (int i = 0, n = opens.size(); i < n; i++) {
        opens.get(i).accept(moduleVisitor);
      }
    }
    if (uses != null) {
      for (int i = 0, n = uses.size(); i < n; i++) {
        moduleVisitor.visitUse(uses.get(i));
      }
    }
    if (provides != null) {
      for (int i = 0, n = provides.size(); i < n; i++) {
        provides.get(i).accept(moduleVisitor);
      }
    }
  }
}

package org.honton.chas.analyzer.asm.visitors;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureVisitor;

/**
 * Tests <code>DependencyVisitor</code>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
 class DependencyVisitorTest {
  private final ResultCollector resultCollector = new ResultCollector();
  private DefaultClassVisitor visitor;
  private MethodVisitor mv;

  @BeforeEach
  void setUp() {
    AnnotationVisitor annotationVisitor = new DefaultAnnotationVisitor(resultCollector);
    SignatureVisitor signatureVisitor = new DefaultSignatureVisitor(resultCollector);
    FieldVisitor fieldVisitor = new DefaultFieldVisitor(annotationVisitor, resultCollector);
    mv = new DefaultMethodVisitor(annotationVisitor, signatureVisitor, resultCollector);
    visitor =
        new DefaultClassVisitor(
            signatureVisitor, annotationVisitor, fieldVisitor, mv, resultCollector);
  }

  @Test
  void testVisitWithDefaultSuperclass() {
    // class a.b.c
    visitor.visit(50, 0, "a/b/c", null, "java/lang/Object", null);

    Assertions.assertEquals(Set.of("java.lang.Object"), resultCollector.getDependencies());
  }

  @Test
  void testVisitWithSuperclass() {
    // class a.b.c
    visitor.visit(50, 0, "a/b/c", null, "x/y/z", null);

    Assertions.assertEquals(Set.of("x.y.z"), resultCollector.getDependencies());
  }

  @Test
  void testVisitWithInterface() {
    // class a.b.c implements x.y.z
    visitor.visit(50, 0, "a/b/c", null, "java/lang/Object", new String[] {"x/y/z"});

    Assertions.assertEquals(Set.of("java.lang.Object", "x.y.z"), resultCollector.getDependencies());
  }

  @Test
  void testVisitWithInterfaces() {
    // class a.b.c implements p.q.r, x.y.z
    visitor.visit(50, 0, "a/b/c", null, "java/lang/Object", new String[] {"p/q/r", "x/y/z"});

    Assertions.assertEquals(Set.of("java.lang.Object", "p.q.r", "x.y.z"), resultCollector.getDependencies());
   }

  @Test
  void testVisitWithUnboundedClassTypeParameter() {
    // class a.b.c<T>
    String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;";

    visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", null);

    Assertions.assertEquals(Set.of("java.lang.Object"), resultCollector.getDependencies());
  }

  @Test
  void testVisitWithBoundedClassTypeParameter() {
    // class a.b.c<T extends x.y.z>
    String signature = "<T:Lx/y/z;>Ljava/lang/Object;";

    visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", null);

    Assertions.assertEquals(Set.of("java.lang.Object", "x.y.z"), resultCollector.getDependencies());
  }

  @Test
  void testVisitWithBoundedClassTypeParameters() {
    // class a.b.c<K extends p.q.r, V extends x.y.z>
    String signature = "<K:Lp/q/r;V:Lx/y/z;>Ljava/lang/Object;";

    visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", null);

    Assertions.assertEquals(Set.of("java.lang.Object", "p.q.r", "x.y.z"), resultCollector.getDependencies());

  }

  @Test
  void testVisitWithGenericInterface() {
    // class a.b.c implements p.q.r<x.y.z>
    String signature = "Ljava/lang/Object;Lp/q/r<Lx/y/z;>;";

    visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", new String[] {"p.q.r"});

    Assertions.assertEquals(Set.of("java.lang.Object", "p.q.r", "x.y.z"), resultCollector.getDependencies());
  }

  @Test
  void testVisitWithInterfaceBound() {
    // class a.b.c<T> implements x.y.z<T>
    String signature = "<T:Ljava/lang/Object;>Ljava/lang/Object;Lx/y/z<TT;>;";

    visitor.visit(50, 0, "a/b/c", signature, "java/lang/Object", new String[] {"x.y.z"});
    
    Assertions.assertEquals(Set.of("java.lang.Object", "x.y.z"), resultCollector.getDependencies());
  }

  // visitSource tests ------------------------------------------------------

  @Test
  void testVisitSource() {
    visitor.visitSource(null, null);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitOuterClass tests --------------------------------------------------

  @Test
  void testVisitOuterClass() {
    // class a.b.c
    // {
    //     class ...
    //     {
    //     }
    // }
    visitor.visitOuterClass("a/b/c", null, null);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitOuterClassInMethod() {
    // class a.b.c
    // {
    //     x.y.z x(p.q.r p)
    //     {
    //         class ...
    //         {
    //         }
    //     }
    // }
    visitor.visitOuterClass("a/b/c", "x", "(Lp/q/r;)Lx/y/z;");

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitAnnotation tests --------------------------------------------------

  @Test
  void testVisitAnnotation() {
    assertVisitor(visitor.visitAnnotation("La/b/c;", false));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitAnnotationWithRuntimeVisibility() {
    assertVisitor(visitor.visitAnnotation("La/b/c;", true));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  // visitAttribute tests ---------------------------------------------------

  @Test
  void testVisitAttribute() {
    visitor.visitAttribute(new MockAttribute("a"));

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitInnerClass tests --------------------------------------------------

  @Test
  void testVisitInnerClass() {
    // TODO: ensure innerName is correct

    // class a.b.c { class x.y.z { } }
    visitor.visitInnerClass("x/y/z", "a/b/c", "z", 0);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitInnerClassAnonymous() {
    // class a.b.c { new class x.y.z { } }
    visitor.visitInnerClass("x/y/z$1", "a/b/c", null, 0);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitField tests -------------------------------------------------------

  @Test
  void testVisitField() {
    // a.b.c a
    assertVisitor(visitor.visitField(0, "a", "La/b/c;", null, null));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitFieldArray() {
    // a.b.c[] a
    assertVisitor(visitor.visitField(0, "a", "[La/b/c;", null, null));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitFieldGeneric() {
    // a.b.c<x.y.z> a
    assertVisitor(visitor.visitField(0, "a", "La/b/c;", "La/b/c<Lx/y/z;>;", null));

    Assertions.assertEquals(Set.of("a.b.c", "x.y.z"), resultCollector.getDependencies());
  }

  // visitMethod tests ------------------------------------------------------

  @Test
  void testVisitMethod() {
    // void a()
    assertVisitor(visitor.visitMethod(0, "a", "()V", null, null));

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithPrimitiveArgument() {
    // void a(int)
    assertVisitor(visitor.visitMethod(0, "a", "(I)V", null, null));

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithPrimitiveArrayArgument() {
    // void a(int[])
    assertVisitor(visitor.visitMethod(0, "a", "([I)V", null, null));

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithObjectArgument() {
    // void a(a.b.c)
    assertVisitor(visitor.visitMethod(0, "a", "(La/b/c;)V", null, null));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithObjectArguments() {
    // void a(a.b.c, x.y.z)
    assertVisitor(visitor.visitMethod(0, "a", "(La/b/c;Lx/y/z;)V", null, null));

    Assertions.assertEquals(Set.of("a.b.c", "x.y.z"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithObjectArrayArgument() {
    // void a(a.b.c[])
    assertVisitor(visitor.visitMethod(0, "a", "([La/b/c;)V", null, null));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithGenericArgument() {
    // void a(a.b.c<x.y.z>)
    assertVisitor(visitor.visitMethod(0, "a", "(La/b/c;)V", "(La/b/c<Lx/y/z;>;)V", null));

    Assertions.assertEquals(Set.of("a.b.c", "x.y.z"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithPrimitiveReturnType() {
    // int a()
    assertVisitor(visitor.visitMethod(0, "a", "()I", null, null));

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithPrimitiveArrayReturnType() {
    // int[] a()
    assertVisitor(visitor.visitMethod(0, "a", "()[I", null, null));

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithObjectReturnType() {
    // a.b.c a()
    assertVisitor(visitor.visitMethod(0, "a", "()La/b/c;", null, null));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithObjectArrayReturnType() {
    // a.b.c[] a()
    assertVisitor(visitor.visitMethod(0, "a", "()[La/b/c;", null, null));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithException() {
    // void a() throws a.b.c
    assertVisitor(visitor.visitMethod(0, "a", "()V", null, new String[] {"a/b/c"}));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodWithExceptions() {
    // void a() throws a.b.c, x.y.z
    assertVisitor(visitor.visitMethod(0, "a", "()V", null, new String[] {"a/b/c", "x/y/z"}));

    Assertions.assertEquals(Set.of("a.b.c", "x.y.z"), resultCollector.getDependencies());
  }

  // visitAnnotationDefault tests -------------------------------------------

  @Test
  void testVisitAnnotationDefault() {
    assertVisitor(mv.visitAnnotationDefault());
    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitParameterAnnotation tests -------------------------------------------

  @Test
  void testVisitParameterAnnotation() {
    // @a.b.c
    assertVisitor(mv.visitParameterAnnotation(0, "La/b/c;", false));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  // visitCode tests --------------------------------------------------------

  @Test
  void testVisitCode() {
    mv.visitCode();

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitFrame tests -------------------------------------------------------

  @Test
  void testVisitFrame() {
    mv.visitFrame(Opcodes.F_NEW, 0, new Object[0], 0, new Object[0]);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitInsn tests --------------------------------------------------------

  @Test
  void testVisitInsn() {
    mv.visitInsn(Opcodes.NOP);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitIntInsn tests -----------------------------------------------------

  @Test
  void testVisitIntInsn() {
    mv.visitIntInsn(Opcodes.BIPUSH, 0);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitVarInsn tests -----------------------------------------------------

  @Test
  void testVisitVarInsn() {
    mv.visitVarInsn(Opcodes.ILOAD, 0);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitTypeInsn tests ----------------------------------------------------

  @Test
  void testVisitTypeInsn() {
    mv.visitTypeInsn(Opcodes.NEW, "a/b/c");

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  // visitFieldInsn tests ---------------------------------------------------

  @Test
  void testVisitFieldInsnWithPrimitive() {
    mv.visitFieldInsn(Opcodes.GETFIELD, "a/b/c", "x", "I");

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitFieldInsnWithObject() {
    mv.visitFieldInsn(Opcodes.GETFIELD, "a/b/c", "x", "Lx/y/z;");

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  // visitMethodInsn tests --------------------------------------------------

  @Test
  void testVisitMethodInsn() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()V", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodInsnWithPrimitiveArgument() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(I)V", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodInsnWithPrimitiveArrayArgument() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([I)V", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodInsnWithObjectArgument() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lx/y/z;)V", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodInsnWithObjectArguments() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "(Lp/q/r;Lx/y/z;)V", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodInsnWithObjectArrayArgument() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "([Lx/y/z;)V", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodInsnWithPrimitiveReturnType() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()I", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodInsnWithPrimitiveArrayReturnType() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[I", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodInsnWithObjectReturnType() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()Lx/y/z;", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitMethodInsnWithObjectArrayReturnType() {
    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "a/b/c", "x", "()[Lx/y/z;", false);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  // visitJumpInsn tests ----------------------------------------------------

  @Test
  void testVisitJumpInsn() {
    mv.visitJumpInsn(Opcodes.IFEQ, new Label());
    
    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitLabel tests -------------------------------------------------------

  @Test
  void testVisitLabel() {
    mv.visitLabel(new Label());

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitLdcInsn tests -----------------------------------------------------

  @Test
  void testVisitLdcInsnWithNonType() {
    mv.visitLdcInsn("a");

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitLdcInsnWithPrimitiveType() {
    mv.visitLdcInsn(Type.INT_TYPE);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitLdcInsnWithObjectType() {
    mv.visitLdcInsn(Type.getType("La/b/c;"));

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  // visitIincInsn tests ----------------------------------------------------

  @Test
  void testVisitIincInsn() {
    mv.visitIincInsn(0, 1);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitTableSwitchInsn tests ---------------------------------------------

  @Test
  void testVisitTableSwitchInsn() {
    mv.visitTableSwitchInsn(0, 1, new Label(), new Label());

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitLookupSwitchInsn tests --------------------------------------------

  @Test
  void testVisitLookupSwitchInsn() {
    mv.visitLookupSwitchInsn(new Label(), new int[] {0}, new Label[] {new Label()});

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitMultiANewArrayInsn tests ------------------------------------------

  @Test
  void testVisitMultiANewArrayInsnWithPrimitive() {
    mv.visitMultiANewArrayInsn("I", 2);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitMultiANewArrayInsnWithObject() {
    mv.visitMultiANewArrayInsn("La/b/c;", 2);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  // visitTryCatchBlock tests -----------------------------------------------

  @Test
  void testVisitTryCatchBlock() {
    mv.visitTryCatchBlock(new Label(), new Label(), new Label(), "a/b/c");

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitTryCatchBlockForFinally() {
    mv.visitTryCatchBlock(new Label(), new Label(), new Label(), null);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitLocalVariable tests -----------------------------------------------

  @Test
  void testVisitLocalVariableWithPrimitive() {
    mv.visitLocalVariable("a", "I", null, new Label(), new Label(), 0);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitLocalVariableWithPrimitiveArray() {
    mv.visitLocalVariable("a", "[I", null, new Label(), new Label(), 0);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  @Test
  void testVisitLocalVariableWithObject() {
    mv.visitLocalVariable("a", "La/b/c;", null, new Label(), new Label(), 0);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitLocalVariableWithObjectArray() {
    mv.visitLocalVariable("a", "[La/b/c;", null, new Label(), new Label(), 0);

    Assertions.assertEquals(Set.of("a.b.c"), resultCollector.getDependencies());
  }

  @Test
  void testVisitLocalVariableWithGenericObject() {
    mv.visitLocalVariable("a", "La/b/c;", "La/b/c<Lx/y/z;>;", new Label(), new Label(), 0);

    Assertions.assertEquals(Set.of("a.b.c", "x.y.z"), resultCollector.getDependencies());
  }

  @Test
  void testVisitLocalVariableWithGenericObjectArray() {
    mv.visitLocalVariable("a", "La/b/c;", "[La/b/c<Lx/y/z;>;", new Label(), new Label(), 0);

    Assertions.assertEquals(Set.of("a.b.c", "x.y.z"), resultCollector.getDependencies());
  }

  // visitLineNumber tests --------------------------------------------------

  @Test
  void testVisitLineNumber() {
    mv.visitLineNumber(0, new Label());

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  // visitMaxs tests --------------------------------------------------------

  @Test
  void testVisitMaxs() {
    mv.visitMaxs(0, 0);

    Assertions.assertEquals(Set.of(), resultCollector.getDependencies());
  }

  private void assertVisitor(Object actualVisitor) {
    // assertEquals( visitor, actualVisitor );
  }

  /**
   * A simple ASM <code>Attribute</code> for use in tests.
   *
   * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
   */
  static class MockAttribute extends Attribute {
    MockAttribute(String type) {
      super(type);
    }
  }
}

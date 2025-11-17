package org.honton.chas.dependency.analyzer;

import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AnnotationValue.OfAnnotation;
import java.lang.classfile.AnnotationValue.OfArray;
import java.lang.classfile.AnnotationValue.OfClass;
import java.lang.classfile.AnnotationValue.OfEnum;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassFile.AttributesProcessingOption;
import java.lang.classfile.ClassFile.DebugElementsOption;
import java.lang.classfile.ClassFile.LineNumbersOption;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.Getter;
import org.honton.chas.dependency.collector.PathCollector.BytesSupplier;

/** Analyze a class */
public class ClassFileAnalyzer {

  @Getter private final Set<ClassDesc> dependencies = new HashSet<>();

  /**
   * Analyze the given class
   *
   * @param byteSupplier the class's content
   */
  public ClassFileAnalyzer(BytesSupplier byteSupplier) throws IOException {
    ClassFile classFile =
        ClassFile.of(
            AttributesProcessingOption.DROP_UNKNOWN_ATTRIBUTES,
            DebugElementsOption.DROP_DEBUG,
            LineNumbersOption.DROP_LINE_NUMBERS);
    ClassModel classModel = classFile.parse(byteSupplier.contents());
    classModel.constantPool().forEach(this::parsePoolEntry);

    classModel.fields().forEach(this::parseFieldModel);
    classModel.methods().forEach(this::parseMethodModel);

    parseAnnotationAttributes(classModel);
  }

  private void parseAnnotationAttributes(AttributedElement ae) {
    // appears on classes, fields, methods, and record components
    visible(ae.findAttributes(Attributes.runtimeVisibleAnnotations()));
    invisible(ae.findAttributes(Attributes.runtimeInvisibleAnnotations()));
    visibleType(ae.findAttributes(Attributes.runtimeVisibleTypeAnnotations()));
    invisibleType(ae.findAttributes(Attributes.runtimeInvisibleTypeAnnotations()));
  }

  private void visible(List<RuntimeVisibleAnnotationsAttribute> attributes) {
    streamList(attributes.stream().map(RuntimeVisibleAnnotationsAttribute::annotations));
  }

  private void invisible(List<RuntimeInvisibleAnnotationsAttribute> attributes) {
    streamList(attributes.stream().map(RuntimeInvisibleAnnotationsAttribute::annotations));
  }

  private void visibleType(List<RuntimeVisibleTypeAnnotationsAttribute> attributes) {
    typeAnnotations(attributes.stream().map(RuntimeVisibleTypeAnnotationsAttribute::annotations));
  }

  private void invisibleType(List<RuntimeInvisibleTypeAnnotationsAttribute> attributes) {
    typeAnnotations(attributes.stream().map(RuntimeInvisibleTypeAnnotationsAttribute::annotations));
  }

  private void typeAnnotations(Stream<List<TypeAnnotation>> annotations) {
    annotations.forEach(
        l -> l.stream().map(TypeAnnotation::annotation).forEach(this::parseAnnotation));
  }

  private void parseParameterAnnotationAttributes(MethodModel mm) {
    visibleParameter(mm.findAttributes(Attributes.runtimeVisibleParameterAnnotations()));
    invisibleParameter(mm.findAttributes(Attributes.runtimeInvisibleParameterAnnotations()));
  }

  private void visibleParameter(List<RuntimeVisibleParameterAnnotationsAttribute> attributes) {
    streamListList(
        attributes.stream().map(RuntimeVisibleParameterAnnotationsAttribute::parameterAnnotations));
  }

  private void invisibleParameter(List<RuntimeInvisibleParameterAnnotationsAttribute> attributes) {
    streamListList(
        attributes.stream()
            .map(RuntimeInvisibleParameterAnnotationsAttribute::parameterAnnotations));
  }

  private void streamListList(Stream<List<List<Annotation>>> annotations) {
    annotations.forEach(list -> streamList(list.stream()));
  }

  private void streamList(Stream<List<Annotation>> annotations) {
    annotations.forEach(list -> list.forEach(this::parseAnnotation));
  }

  private void parseAnnotation(Annotation annotation) {
    addClassDesc(annotation.classSymbol());
    annotation.elements().stream()
        .map(AnnotationElement::value)
        .forEach(this::parseAnnotationValue);
  }

  private void parseAnnotationValue(AnnotationValue value) {
    switch (value) {
      case OfAnnotation ofAnnotation -> parseAnnotation(ofAnnotation.annotation());
      case OfArray ofArray -> ofArray.values().forEach(this::parseAnnotationValue);
      case OfClass ofClass -> addClassDesc(ofClass.classSymbol());
      case OfEnum ofEnum -> addClassDesc(ofEnum.classSymbol());
      default -> {}
    }
  }

  private void parsePoolEntry(PoolEntry poolEntry) {
    switch (poolEntry) {
      case ClassEntry classEntry -> addClassDesc(classEntry.asSymbol());
      case ConstantDynamicEntry constantDynamicEntry ->
          addClassDesc(constantDynamicEntry.typeSymbol());
      case FieldRefEntry fieldRefEntry -> addClassDesc(fieldRefEntry.typeSymbol());
      case InterfaceMethodRefEntry interfaceMethodRefEntry ->
          parseMethodTypeDesc(interfaceMethodRefEntry.typeSymbol());
      case MemberRefEntry memberRefEntry -> addClassDesc(memberRefEntry.owner().asSymbol());
      case MethodTypeEntry methodTypeEntry -> parseMethodTypeDesc(methodTypeEntry.asSymbol());
      case NameAndTypeEntry nameAndTypeEntry -> {
        // representing a field or method
        // method descriptor strings if it starts with (.
        // field descriptor string, and must start with one of the BCDFIJSZL[ characters.
        String descriptor = nameAndTypeEntry.type().stringValue();
        if (descriptor.charAt(0) == '(') {
          parseMethodTypeDesc(MethodTypeDesc.ofDescriptor(descriptor));
        } else {
          addClassDesc(ClassDesc.ofDescriptor(descriptor));
        }
      }
      default -> {}
    }
  }

  void addClassDesc(ClassDesc classDesc) {
    if (classDesc.isArray()) {
      addClassDesc(classDesc.componentType());
    } else if (classDesc.isClassOrInterface()) {
      dependencies.add(classDesc);
    } else if (!classDesc.isPrimitive()) {
      throw new IllegalArgumentException(
          "classDesc is unexpected type " + classDesc.descriptorString());
    }
  }

  private void parseFieldModel(FieldModel fieldModel) {
    addClassDesc(fieldModel.fieldTypeSymbol());
    parseAnnotationAttributes(fieldModel);
  }

  private void parseMethodModel(MethodModel methodModel) {
    parseMethodTypeDesc(methodModel.methodTypeSymbol());
    parseAnnotationAttributes(methodModel);
    parseParameterAnnotationAttributes(methodModel);
  }

  private void parseMethodTypeDesc(MethodTypeDesc mts) {
    dependencies.add(mts.returnType());
    dependencies.addAll(mts.parameterList());
  }
}

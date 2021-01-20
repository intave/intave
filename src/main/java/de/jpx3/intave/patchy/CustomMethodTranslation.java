package de.jpx3.intave.patchy;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.lib.asm.tree.AnnotationNode;
import de.jpx3.intave.patchy.annotate.PatchyUnknownVersionPolicy;
import de.jpx3.intave.tools.annotate.Native;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomMethodTranslation {
  private PatchyUnknownVersionPolicy versionPolicy;
  private List<VersionMethodReference> versionMethodReferences = new ArrayList<>();
  private VersionMethodReference compiledTranslation;

  @Native
  public VersionMethodReference selectedTranslationOf(VersionMethodReference original) {
    if (compiledTranslation == null) {
      compiledTranslation = compileTranslation(original);
    }
    return compiledTranslation;
  }

  @Native
  private VersionMethodReference compileTranslation(VersionMethodReference original) {
    String version = PatchyTranslationConfiguration.selectSuitableVersion(this, original);
    for (VersionMethodReference versionMethodReference : versionMethodReferences) {
      if (versionMethodReference.version().equals(version)) {
        return versionMethodReference;
      }
    }
    throw new IllegalStateException("Something went wrong");
  }

  public PatchyUnknownVersionPolicy versionPolicy() {
    return versionPolicy;
  }

  public List<VersionMethodReference> versionMethodDescriptors() {
    return versionMethodReferences;
  }

  @Native
  public static CustomMethodTranslation buildFrom(AnnotationNode annotationNode) {
    if (!PatchyTranslationConfiguration.className(annotationNode).equals(PatchyTranslationConfiguration.CUSTOM_METHOD_TRANSLATION_ANNOTATION_PATH)) {
      throw new IllegalArgumentException("Invalid annotation type");
    }
    CustomMethodTranslation customMethodTranslation = new CustomMethodTranslation();
    Map<String, Object> stringObjectMap = PatchyTranslationConfiguration.buildAnnotationMap(annotationNode.values);
    if (stringObjectMap.containsKey("unknownVersionPolicy")) {
      customMethodTranslation.versionPolicy = Enum.valueOf(PatchyUnknownVersionPolicy.class, ((String[]) stringObjectMap.get("unknownVersionPolicy"))[1]);
    } else {
      customMethodTranslation.versionPolicy = PatchyUnknownVersionPolicy.USE_NEXT_LOWER;
    }
    //noinspection unchecked
    for (AnnotationNode value : (List<AnnotationNode>) stringObjectMap.get("value")) {
      customMethodTranslation.versionMethodReferences.add(VersionMethodReference.buildFrom(value));
    }
    customMethodTranslation.versionMethodReferences = ImmutableList.copyOf(customMethodTranslation.versionMethodReferences);
    return customMethodTranslation;
  }
}

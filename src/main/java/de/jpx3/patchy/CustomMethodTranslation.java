package de.jpx3.patchy;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.lib.asm.tree.AnnotationNode;
import de.jpx3.intave.tools.annotate.Natify;
import de.jpx3.patchy.annotate.PatchyUnknownVersionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomMethodTranslation {
  private PatchyUnknownVersionPolicy versionPolicy;
  private List<VersionMethodReference> versionMethodReferences = new ArrayList<>();
  private VersionMethodReference compiledTranslation;

  @Natify
  public VersionMethodReference selectedTranslationOf(VersionMethodReference original) {
    if (compiledTranslation == null) {
      compiledTranslation = compileTranslation(original);
    }
    return compiledTranslation;
  }

  @Natify
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

  @Natify
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

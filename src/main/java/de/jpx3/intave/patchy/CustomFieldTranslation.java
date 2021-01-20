package de.jpx3.intave.patchy;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.lib.asm.tree.AnnotationNode;
import de.jpx3.intave.patchy.annotate.PatchyUnknownVersionPolicy;
import de.jpx3.intave.tools.annotate.Native;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CustomFieldTranslation {
  private PatchyUnknownVersionPolicy versionPolicy;
  private List<VersionFieldReference> versionFieldReferences = new ArrayList<>();

  public PatchyUnknownVersionPolicy versionPolicy() {
    return versionPolicy;
  }

  public List<VersionFieldReference> versionFieldDescriptors() {
    return versionFieldReferences;
  }

  @Native
  public static CustomFieldTranslation buildFrom(AnnotationNode annotationNode) {
    if (!PatchyTranslationConfiguration.className(annotationNode).equals(PatchyTranslationConfiguration.CUSTOM_FIELD_TRANSLATION_ANNOTATION_PATH)) {
      throw new IllegalArgumentException("Invalid annotation type");
    }
    CustomFieldTranslation customFieldTranslation = new CustomFieldTranslation();
    Map<String, Object> stringObjectMap = PatchyTranslationConfiguration.buildAnnotationMap(annotationNode.values);
    if (stringObjectMap.containsKey("unknownVersionPolicy")) {
      customFieldTranslation.versionPolicy = Enum.valueOf(PatchyUnknownVersionPolicy.class, ((String[]) stringObjectMap.get("unknownVersionPolicy"))[1]);
    } else {
      customFieldTranslation.versionPolicy = PatchyUnknownVersionPolicy.USE_NEXT_LOWER;
    }
    //noinspection unchecked
    for (AnnotationNode value : (List<AnnotationNode>) stringObjectMap.get("value")) {
      customFieldTranslation.versionFieldReferences.add(VersionFieldReference.buildFrom(value));
    }
    customFieldTranslation.versionFieldReferences = ImmutableList.copyOf(customFieldTranslation.versionFieldReferences);
    return customFieldTranslation;
  }
}

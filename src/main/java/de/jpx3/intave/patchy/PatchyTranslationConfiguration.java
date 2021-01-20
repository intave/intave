package de.jpx3.intave.patchy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.lib.asm.Type;
import de.jpx3.intave.lib.asm.tree.AnnotationNode;
import de.jpx3.intave.lib.asm.tree.ClassNode;
import de.jpx3.intave.lib.asm.tree.MethodNode;
import de.jpx3.intave.patchy.annotate.*;
import de.jpx3.intave.tools.annotate.Native;

import java.util.*;

final class PatchyTranslationConfiguration {
  static final String AUTO_TRANSLATION_ANNOTATION_PATH = slashify(PatchyAutoTranslation.class.getName());
  static final String CUSTOM_METHOD_TRANSLATION_ANNOTATION_PATH = slashify(PatchyCustomMethodTranslation.class.getName());
  static final String VERSION_METHOD_REFERENCE_ANNOTATION_PATH = slashify(PatchyVersionMethodReference.class.getName());
  static final String CUSTOM_FIELD_TRANSLATION_ANNOTATION_PATH = slashify(PatchyCustomFieldTranslation.class.getName());
  static final String VERSION_FIELD_REFERENCE_ANNOTATION_PATH = slashify(PatchyVersionFieldReference.class.getName());
  static final String TRANSLATE_PARAMETERS_ANNOTATION_PATH = slashify(PatchyTranslateParameters.class.getName());

  private boolean translateEverything = false;
  private boolean translateParameters = false;

  private List<CustomMethodTranslation> customMethodTranslationList = new ArrayList<>();
  private List<CustomFieldTranslation> customFieldTranslations = new ArrayList<>();

  public List<CustomMethodTranslation> customMethodTranslationList() {
    return customMethodTranslationList;
  }
  public List<CustomFieldTranslation> customFieldTranslations() {
    return customFieldTranslations;
  }

  @Native
  public VersionMethodReference resolveCustomMethodDescriptor(String owner, String name, String descriptor) {
    CustomMethodTranslation selectedTranslation = null;
    VersionMethodReference originalMethodReference = null;
    for (CustomMethodTranslation customMethodTranslation : customMethodTranslationList) {
      for (VersionMethodReference versionMethodReference : customMethodTranslation.versionMethodDescriptors()) {
        if(versionMethodReference.sameTarget(owner, name, descriptor)) {
          originalMethodReference = versionMethodReference;
          selectedTranslation = customMethodTranslation;
          break;
        }
      }
    }
//    System.out.println("Selection: " + selectedTranslation);
    if(selectedTranslation == null) {
      return null;
    }
//    System.out.println(selectedTranslation.selectedTranslationOf(originalMethodReference));
    return selectedTranslation.selectedTranslationOf(originalMethodReference);
  }

  public boolean translateEverything() {
    return translateEverything;
  }

  public boolean translateParameters() {
    return translateParameters;
  }

  static Map<String, Object> buildAnnotationMap(List<Object> objects) {
    if(objects == null) {
      return ImmutableMap.of();
    }
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < objects.size(); i += 2) {
      map.put((String) objects.get(i), objects.get(i + 1));
    }
    return ImmutableMap.copyOf(map);
  }

  @Native
  static String selectSuitableVersion(CustomMethodTranslation selectedTranslation, VersionMethodReference originalMethod) {
    String serverVersion = PatchyTranslator.CURRENT_SERVER_VERSION;
    String selectedVersion = null;
    List<String> availableVersions = new ArrayList<>();
    for (VersionMethodReference versionMethodReference : selectedTranslation.versionMethodDescriptors()) {
      availableVersions.add(versionMethodReference.version());
    }
    availableVersions.sort(Comparator.comparingInt(PatchyTranslationConfiguration::versionToInt).reversed());
//    System.out.println(availableVersions);
    boolean nativeVersionAvailable = availableVersions.contains(serverVersion);
    switch (selectedTranslation.versionPolicy()) {
      case IGNORE:
        if(!nativeVersionAvailable) {
          return originalMethod.version();
        }
        selectedVersion = serverVersion;
        break;
      case THROW_ERROR:
        if(!nativeVersionAvailable) {
          throw new MissingVersionTranslationException("Unable to find translation for version " + serverVersion + " when referring to " + originalMethod.owner() + "#" + originalMethod.name() + originalMethod.version());
        }
        selectedVersion = serverVersion;
        break;
      case USE_NEXT_LOWER:
        for (String availableVersion : availableVersions) {
          if(selectedVersion == null || newerThan(selectedVersion, availableVersion) && !newerThan(availableVersion, serverVersion)) {
            selectedVersion = availableVersion;
          }
        }
        if(selectedVersion == null) {
          throw new IllegalStateException("Something went a bit wrong here");
        }
        break;
      case USE_NEXT_HIGHER:
        for (String availableVersion : availableVersions) {
          if((selectedVersion == null || newerThan(selectedVersion, availableVersion)) && selectedVersion == null || !newerThan(selectedVersion, serverVersion)) {
            selectedVersion = availableVersion;
          }
        }
        if(selectedVersion == null) {
          throw new IllegalStateException("Something went a bit wrong here");
        }
        break;
    }
    return selectedVersion;
  }

  private static boolean newerThan(String mightBeOlder, String toCheck) {
    return versionToInt(mightBeOlder) < versionToInt(toCheck);
  }

  private static final String REPLACE_ALL_NON_NUMBERS_REGEX = "[^\\d.]";

  private static int versionToInt(String version) {
    String onlyIntegers = version.replaceAll(REPLACE_ALL_NON_NUMBERS_REGEX, "");
    return Integer.parseInt(onlyIntegers);
  }

  @Native
  public static PatchyTranslationConfiguration createFrom(ClassNode classNode) {
    PatchyTranslationConfiguration configuration = new PatchyTranslationConfiguration();
    classNode.visibleAnnotations.forEach(annotation -> processAnnotation(configuration, annotation));
    return configuration;
  }

  @Native
  public static PatchyTranslationConfiguration createFrom(MethodNode methodNode) {
    PatchyTranslationConfiguration configuration = new PatchyTranslationConfiguration();
    annotationsOf(methodNode).forEach(annotation -> processAnnotation(configuration, annotation));
    configuration.customMethodTranslationList = ImmutableList.copyOf(configuration.customMethodTranslationList);
    configuration.customFieldTranslations = ImmutableList.copyOf(configuration.customFieldTranslations);
    return configuration;
  }

  private static List<AnnotationNode> annotationsOf(MethodNode methodNode) {
    List<AnnotationNode> annotationNodes = new ArrayList<>();
    annotationNodes.addAll(methodNode.visibleAnnotations == null ? Collections.emptyList() : methodNode.visibleAnnotations);
    annotationNodes.addAll(methodNode.visibleTypeAnnotations == null ? Collections.emptyList() : methodNode.visibleTypeAnnotations);
    return annotationNodes;
  }

  @Native
  private static void processAnnotation(PatchyTranslationConfiguration configuration, AnnotationNode annotation) {
    String className = className(annotation);
    if(className.equals(AUTO_TRANSLATION_ANNOTATION_PATH)) {
      configuration.translateEverything = true;
    } else if (className.equals(CUSTOM_METHOD_TRANSLATION_ANNOTATION_PATH)) {
      configuration.customMethodTranslationList.add(CustomMethodTranslation.buildFrom(annotation));
    } else if (className.equals(CUSTOM_FIELD_TRANSLATION_ANNOTATION_PATH)) {
      configuration.customFieldTranslations.add(CustomFieldTranslation.buildFrom(annotation));
    } if(className.equals(TRANSLATE_PARAMETERS_ANNOTATION_PATH)) {
      configuration.translateParameters = true;
    }
  }

  static String className(AnnotationNode annotationNode) {
    return slashify(Type.getType(annotationNode.desc).getClassName());
  }

  private static String slashify(String input) {
    return input.replace('.', '/');
  }
}

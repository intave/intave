package de.jpx3.intave.patchy;

import de.jpx3.intave.lib.asm.tree.AnnotationNode;

import java.util.List;
import java.util.Map;

public final class VersionMethodReference {
  private final String version;
  private final String owner;
  private final String name;
  private final String description;

  public VersionMethodReference(String version, String owner, String name, String description) {
    this.version = version;
    this.owner = owner;
    this.name = name;
    this.description = description;
  }

  public String version() {
    return version;
  }

  public String owner() {
    return owner;
  }

  public String name() {
    return name;
  }

  public String description() {
    return description;
  }

  public boolean sameTarget(String owner, String name, String description) {
    return
      (owner.equals(this.owner)) &&
        (name.equals(this.name)) &&
        (description.equals(this.description));
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String version;
    private String owner;
    private String name;
    private String description;

    public Builder withVersion(String version) {
      this.version = version;
      return this;
    }

    public Builder withOwner(String owner) {
      this.owner = owner;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withDescription(String description) {
      this.description = description;
      return this;
    }

    public VersionMethodReference build() {
      return new VersionMethodReference(version, owner, name, description);
    }
  }

  public static VersionMethodReference buildFrom(AnnotationNode annotationNode) {
    if (!PatchyTranslationConfiguration.className(annotationNode).equals(PatchyTranslationConfiguration.VERSION_METHOD_REFERENCE_ANNOTATION_PATH)) {
      throw new IllegalArgumentException("Invalid annotation type");
    }
    List<Object> values = annotationNode.values;
    Map<String, Object> annotationElementMap = PatchyTranslationConfiguration.buildAnnotationMap(values);
    return VersionMethodReference.builder()
      .withVersion((String) annotationElementMap.get("version"))
      .withOwner((String) annotationElementMap.get("owner"))
      .withName((String) annotationElementMap.get("name"))
      .withDescription((String) annotationElementMap.get("desc"))
      .build();
  }
}

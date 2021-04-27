package de.jpx3.intave.reflect.relocate;

import de.jpx3.intave.access.IntaveException;
import de.jpx3.intave.lib.asm.Handle;
import de.jpx3.intave.tools.annotate.KeepEnumInternalNames;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

public final class MappingFileCompiler {
  public static UnivHandleMapper compile(File mappingFile) throws IntaveException {
    verifyFile(mappingFile);
    Map<String, String> classTranslation = new HashMap<>();
    Map<Handle, Handle> handleTranslation = new HashMap<>();
    try {
      Scanner scanner = new Scanner(new FileInputStream(mappingFile));
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        try {
          compileLine(line, classTranslation, handleTranslation);
        } catch (Exception exception) {
          throw new IntaveException("Unable to parse line \"" + line + "\"", exception);
        }
      }
    } catch (FileNotFoundException exception) {
      throw new IntaveException("Unable to load mapping file", exception);
    }
    return new UnivHandleMapper(classTranslation, handleTranslation);
  }

  private static void compileLine(String line, Map<String, String> classTranslation, Map<Handle, Handle> handleTranslation) {
    line = line.toLowerCase(Locale.ROOT).trim().replace("  ", "");
    String finalLine = line;
    TranslationType type = Arrays.stream(TranslationType.values())
      .filter(translationType -> finalLine.startsWith(translationType.name().toLowerCase(Locale.ROOT)))
      .findFirst()
      .orElseThrow(() -> new IntaveException("Unknown applier"));
    line = line.substring(type.name().length() + 1);
    if(!line.startsWith("\"")) {
      throw new IllegalStateException("Parenthesis required at start of original");
    }
    line = line.substring(1);
    StringBuilder originalBuilder = new StringBuilder();
    int index = 0;
    char cha;
    while((cha = line.charAt(index)) != '\"') {
      originalBuilder.append(cha);
      index++;
    }
    line = line.substring(index);
    String original = originalBuilder.toString();
    line = line.substring(1).trim();
    String finalLine1 = line;
    Operator operator = Arrays.stream(Operator.values())
      .filter(selOp -> finalLine1.startsWith(selOp.name().toLowerCase(Locale.ROOT).replace("_", " ")))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("Unknown operator"));
    line = line.substring(operator.name().length());
    line = line.substring(1);
    if(!line.startsWith("\"")) {
      throw new IllegalStateException("Parenthesis required at start of target");
    }
    StringBuilder targetBuilder = new StringBuilder();
    index = 0;
    while((cha = line.charAt(index)) != '\"') {
      targetBuilder.append(cha);
      index++;
    }
    String target = targetBuilder.toString();
    type.applier.apply(original, operator, target, classTranslation, handleTranslation);
  }

  private static void verifyFile(File mappingFile) {
    if(!mappingFile.exists()) {
      throw new IntaveException("Unable to locate file " + mappingFile);
    }
    if(!mappingFile.isDirectory()) {
      throw new IntaveException("Mapping file is directory?");
    }
  }

  @KeepEnumInternalNames
  public enum Operator {
    IS_NOW,
    IS_NOW_CALLED,
    IS_NOW_LOCATED_AT
  }

  @KeepEnumInternalNames
  public enum TranslationType {
    CLASS(new ClassApplier()),
    METHOD(new MethodApplier()),
    FIELD(new FieldApplier())
    ;

    private final Applier applier;

    TranslationType(Applier applier) {
      this.applier = applier;
    }

    public Applier applier() {
      return applier;
    }
  }

  public interface Applier {
    void apply(String original, Operator operator, String target, Map<String, String> classTranslation, Map<Handle, Handle> handleTranslation);
  }

  public static class ClassApplier implements Applier {
    @Override
    public void apply(String original, Operator operator, String target, Map<String, String> classTranslation, Map<Handle, Handle> handleTranslation) {
      switch (operator) {
        case IS_NOW:
          classTranslation.put(original, target);
          break;
        case IS_NOW_CALLED:
          String originalPath = original.substring(0, original.lastIndexOf("."));
          classTranslation.put(original, originalPath + target);
          break;
        case IS_NOW_LOCATED_AT:
          String originalName = original.substring(original.lastIndexOf("."));
          classTranslation.put(original, target + originalName);
          break;
      }
    }
  }

  public static class MethodApplier implements Applier {
    @Override
    public void apply(String original, Operator operator, String target, Map<String, String> classTranslation, Map<Handle, Handle> handleTranslation) {

    }
  }

  public static class FieldApplier implements Applier {
    @Override
    public void apply(String original, Operator operator, String target, Map<String, String> classTranslation, Map<Handle, Handle> handleTranslation) {

    }
  }
}

package com.cobol.parser.processor;

import com.cobol.parser.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This processor is the "brain" of the parser. It analyzes the raw AST
 * to detect the copybook's structural pattern (e.g., multiple independent 01-levels,
 * multiple 01-level REDEFINES, or a shared record type field) and constructs the
 * appropriate record layouts. This version fixes the UnsupportedOperationException.
 */
public class LayoutProcessor implements AstProcessor {

    private enum CopybookPattern {
        MULTIPLE_INDEPENDENT_01_LEVELS,
        MULTIPLE_01_LEVEL_REDEFINES,
        SHARED_RECORD_TYPE,
        UNKNOWN
    }

    @Override
    public void process(ParseResult parseResult) {
        CopybookPattern pattern = detectPattern(parseResult.getReferenceFields());

        switch (pattern) {
            case MULTIPLE_INDEPENDENT_01_LEVELS:
                processIndependent01Levels(parseResult);
                break;
            case MULTIPLE_01_LEVEL_REDEFINES:
                processMultiple01Levels(parseResult);
                break;
            case SHARED_RECORD_TYPE:
                processSharedRecordType(parseResult);
                break;
            default:
                // Handle cases where no specific multi-layout pattern is detected,
                // for example, a simple copybook with only one 01-level record.
                processSingleLayout(parseResult);
                break;
        }
    }

    private CopybookPattern detectPattern(List<CobolField> rootFields) {
        if (rootFields.isEmpty()) return CopybookPattern.UNKNOWN;

        long count01Levels = rootFields.stream().filter(f -> f.getLevel() == 1).count();
        long count01Redefines = rootFields.stream().filter(f -> f.getLevel() == 1 && f.getRedefines() != null).count();

        if (count01Levels > 1 && count01Redefines == 0) {
            return CopybookPattern.MULTIPLE_INDEPENDENT_01_LEVELS;
        }
        if (count01Redefines > 0) {
            return CopybookPattern.MULTIPLE_01_LEVEL_REDEFINES;
        }
        if (rootFields.size() == 1 && findSharedRecordTypeField(rootFields.get(0)) != null) {
            return CopybookPattern.SHARED_RECORD_TYPE;
        }

        return CopybookPattern.UNKNOWN;
    }

    /**
     * Handles copybooks with multiple independent 01-level record definitions.
     */
    private void processIndependent01Levels(ParseResult result) {
        for (CobolField rootField : result.getReferenceFields()) {
            if (rootField.getLevel() == 1) {
                RecordLayout layout = new RecordLayout(rootField.getName());
                layout.setStartPosition(1);
                layout.setLength(rootField.getLength());
                layout.setEndPosition(rootField.getEndPosition());
                layout.setDescription("Independent Record Layout for " + rootField.getName());
                for (CobolField child : rootField.getChildren()) {
                    layout.getFields().add(deepCopy(child));
                }
                result.getRecordLayouts().add(layout);
            }
        }
        // After creating layouts, clear the reference fields as they are now represented in the layouts.
        result.getReferenceFields().clear();
    }

    /**
     * Handles copybooks with multiple 01-level records redefining a single base buffer.
     */
    private void processMultiple01Levels(ParseResult result) {
        CobolField baseRecord = findBaseRecord(result.getReferenceFields());
        if (baseRecord == null) return;

        for (CobolField field : result.getReferenceFields()) {
            if (field.getLevel() == 1 && field.getRedefines() != null) {
                RecordLayout layout = new RecordLayout(field.getName());
                layout.setRedefines(field.getRedefines());
                layout.setStartPosition(1);
                layout.setLength(result.getTotalLength());
                layout.setDescription("Memory overlay of " + field.getRedefines());
                for (CobolField child : field.getChildren()) {
                    layout.getFields().add(deepCopy(child));
                }
                result.getRecordLayouts().add(layout);
            }
        }
        // FIX: Create a new MUTABLE list instead of an immutable one.
        result.setReferenceFields(new ArrayList<>(List.of(baseRecord)));
    }

    /**
     * Handles copybooks with a single 01-level containing a shared record type field.
     */
    private void processSharedRecordType(ParseResult result) {
        CobolField mainRecord = result.getReferenceFields().get(0);
        CobolField recordTypeField = findSharedRecordTypeField(mainRecord);
        if (recordTypeField == null) return;

        for (ConditionName condition : recordTypeField.getConditionNames()) {
            CobolField layoutStructure = findStructureForCondition(mainRecord, condition.getName());
            if (layoutStructure != null) {
                RecordLayout layout = new RecordLayout(layoutStructure.getName());
                layout.setRedefines(layoutStructure.getRedefines());
                layout.setStartPosition(1);
                layout.setLength(result.getTotalLength());
                layout.getRecordTypeValues().add(condition.getValue());
                layout.setDescription(layoutStructure.getName() + " - identified when " + recordTypeField.getName() + " = '" + condition.getValue() + "'");

                layout.getFields().add(deepCopy(recordTypeField));
                for (CobolField child : layoutStructure.getChildren()) {
                    layout.getFields().add(deepCopy(child));
                }
                result.getRecordLayouts().add(layout);
            }
        }
        // FIX: Create a new MUTABLE list instead of an immutable one.
        result.setReferenceFields(new ArrayList<>(List.of(mainRecord)));
    }

    /**
     * Handles simple cases with only one 01-level record and no special patterns.
     */
    private void processSingleLayout(ParseResult result) {
        if (result.getReferenceFields().isEmpty()) return;
        CobolField rootField = result.getReferenceFields().get(0);
        RecordLayout layout = new RecordLayout(rootField.getName());
        layout.setStartPosition(1);
        layout.setLength(rootField.getLength());
        layout.setEndPosition(rootField.getEndPosition());
        layout.setDescription("Primary Record Layout");
        for (CobolField child : rootField.getChildren()) {
            layout.getFields().add(deepCopy(child));
        }
        result.getRecordLayouts().add(layout);
        result.getReferenceFields().clear();
    }

    private CobolField findBaseRecord(List<CobolField> rootFields) {
        return rootFields.stream().filter(f -> f.getLevel() == 1 && f.getRedefines() == null).findFirst().orElse(null);
    }
    private CobolField findSharedRecordTypeField(CobolField mainRecord) {
        for (CobolField child : mainRecord.getChildren()) {
            if (child.getLevel() < 10 && !child.getConditionNames().isEmpty() && child.getRedefines() == null) return child;
        }
        return null;
    }
    private CobolField findStructureForCondition(CobolField mainRecord, String conditionName) {
        String targetPart = "";
        if (conditionName.contains("HDR")) targetPart = "HEADER";
        else if (conditionName.contains("DTL")) targetPart = "DETAIL";
        else if (conditionName.contains("TRL")) targetPart = "TRAILER";
        else return null;
        for (CobolField child : mainRecord.getChildren()) {
            if (child.getName().contains(targetPart)) return child;
        }
        return null;
    }

    private CobolField deepCopy(CobolField original) {
        CobolField copy = new CobolField(original.getLevel(), original.getName());
        copy.setPicture(original.getPicture());
        copy.setStartPosition(original.getStartPosition());
        copy.setEndPosition(original.getEndPosition());
        copy.setLength(original.getLength());
        copy.setDataType(original.getDataType());
        copy.setUsage(original.getUsage());
        copy.setSigned(original.isSigned());
        copy.setDecimal(original.isDecimal());
        copy.setDecimalPlaces(original.getDecimalPlaces());
        copy.setOccursCount(original.getOccursCount());
        copy.setRedefines(original.getRedefines());
        copy.setValue(original.getValue());
        original.getConditionNames().forEach(cn -> copy.getConditionNames().add(new ConditionName(cn.getName(), cn.getValue())));
        original.getChildren().forEach(child -> copy.addChild(deepCopy(child)));
        return copy;
    }
}

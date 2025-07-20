package com.cobol.parser.processor;

import com.cobol.parser.model.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This processor is the "brain" of the parser. It analyzes the raw AST
 * to detect the copybook's structural pattern (e.g., multiple 01-level REDEFINES
 * vs. a shared record type field) and constructs the appropriate record layouts.
 */
public class LayoutProcessor implements AstProcessor {

    private enum CopybookPattern {
        MULTIPLE_01_LEVEL_REDEFINES,
        SHARED_RECORD_TYPE,
        UNKNOWN
    }

    @Override
    public void process(ParseResult parseResult) {
        CopybookPattern pattern = detectPattern(parseResult.getReferenceFields());

        switch (pattern) {
            case MULTIPLE_01_LEVEL_REDEFINES:
                processMultiple01Levels(parseResult);
                break;
            case SHARED_RECORD_TYPE:
                processSharedRecordType(parseResult);
                break;
            default:
                // Handle unknown or simple structures if necessary
                break;
        }
    }

    private CopybookPattern detectPattern(List<CobolField> rootFields) {
        if (rootFields.isEmpty()) return CopybookPattern.UNKNOWN;

        // Check for Multiple 01-Level REDEFINES pattern
        long countOf01Redefines = rootFields.stream()
                .filter(f -> f.getLevel() == 1 && f.getRedefines() != null)
                .count();

        if (countOf01Redefines > 0) {
            return CopybookPattern.MULTIPLE_01_LEVEL_REDEFINES;
        }

        // Check for Shared Record Type pattern (01 has children, one of which has 88-levels)
        CobolField mainRecord = rootFields.get(0);
        boolean hasSharedType = mainRecord.getChildren().stream()
                .anyMatch(child -> child.getLevel() < 10 && !child.getConditionNames().isEmpty());

        if (hasSharedType) {
            return CopybookPattern.SHARED_RECORD_TYPE;
        }

        return CopybookPattern.UNKNOWN;
    }

    /**
     * Processes copybooks with the "Multiple 01-Level REDEFINES" pattern.
     * Example: employee-record.cbl
     */
    private void processMultiple01Levels(ParseResult parseResult) {
        for (CobolField rootField : parseResult.getReferenceFields()) {
            // We only care about the 01-level records that redefine the base buffer.
            if (rootField.getLevel() == 1 && rootField.getRedefines() != null) {
                RecordLayout layout = new RecordLayout(rootField.getName());
                layout.setRedefines(rootField.getRedefines());
                layout.setStartPosition(1); // All redefine the same memory area starting at 1
                layout.setLength(parseResult.getTotalLength());
                layout.setDescription("Record layout for " + rootField.getName());

                // The children of the 01-level field are the actual fields of this layout.
                for (CobolField child : rootField.getChildren()) {
                    layout.getFields().add(deepCopy(child));
                }
                parseResult.getRecordLayouts().add(layout);
            }
        }
    }

    /**
     * Processes copybooks with the "Shared Record Type" pattern.
     * Example: CAONPOST copybook
     */
    private void processSharedRecordType(ParseResult parseResult) {
        if (parseResult.getReferenceFields().isEmpty()) return;

        CobolField mainRecord = parseResult.getReferenceFields().get(0);
        CobolField recordTypeField = findSharedRecordTypeField(mainRecord);

        if (recordTypeField == null) return;

        for (ConditionName condition : recordTypeField.getConditionNames()) {
            CobolField layoutStructure = findStructureForCondition(mainRecord, condition.getName());

            if (layoutStructure != null) {
                RecordLayout layout = new RecordLayout(layoutStructure.getName());
                layout.setRedefines(layoutStructure.getRedefines());
                layout.setStartPosition(1);
                layout.setLength(parseResult.getTotalLength());
                layout.getRecordTypeValues().add(condition.getValue());
                layout.setDescription(layoutStructure.getName() + " - identified when " + recordTypeField.getName() + " = '" + condition.getValue() + "'");

                // Add the shared record type field to this layout
                layout.getFields().add(deepCopy(recordTypeField));

                // Add the specific fields for this layout
                for (CobolField child : layoutStructure.getChildren()) {
                    layout.getFields().add(deepCopy(child));
                }
                parseResult.getRecordLayouts().add(layout);
            }
        }
    }

    private CobolField findSharedRecordTypeField(CobolField mainRecord) {
        for (CobolField child : mainRecord.getChildren()) {
            if (child.getLevel() < 10 && !child.getConditionNames().isEmpty() && child.getRedefines() == null) {
                return child;
            }
        }
        return null;
    }

    private CobolField findStructureForCondition(CobolField mainRecord, String conditionName) {
        String targetNamePart;
        if (conditionName.contains("HDR")) targetNamePart = "HEADER";
        else if (conditionName.contains("DTL")) targetNamePart = "DETAIL";
        else if (conditionName.contains("TRL")) targetNamePart = "TRAILER";
        else return null;

        for (CobolField child : mainRecord.getChildren()) {
            if (child.getName().contains(targetNamePart)) {
                return child;
            }
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

        original.getConditionNames().forEach(cn -> copy.addConditionName(new ConditionName(cn.getName(), cn.getValue())));
        original.getChildren().forEach(child -> copy.addChild(deepCopy(child)));

        return copy;
    }
}

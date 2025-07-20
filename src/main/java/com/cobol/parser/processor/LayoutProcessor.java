package com.cobol.parser.processor;

import com.cobol.parser.model.*;
import java.util.List;

public class LayoutProcessor implements AstProcessor {

    private enum CopybookPattern {
        MULTIPLE_01_LEVELS,
        SHARED_RECORD_TYPE,
        UNKNOWN
    }

    @Override
    public void process(ParseResult parseResult) {
        CopybookPattern pattern = detectPattern(parseResult.getReferenceFields());

        switch (pattern) {
            case MULTIPLE_01_LEVELS:
                processMultiple01Levels(parseResult);
                break;
            case SHARED_RECORD_TYPE:
                processSharedRecordType(parseResult);
                break;
            default:
                // No specific layout pattern detected, do nothing.
                break;
        }
        // The raw AST has served its purpose and is cleared to avoid confusion.
        parseResult.getReferenceFields().clear();
    }

    private CopybookPattern detectPattern(List<CobolField> rootFields) {
        if (rootFields.isEmpty()) {
            return CopybookPattern.UNKNOWN;
        }
        long count01Redefines = rootFields.stream().filter(f -> f.getLevel() == 1 && f.getRedefines() != null).count();
        if (count01Redefines > 0) {
            return CopybookPattern.MULTIPLE_01_LEVELS;
        }
        if (rootFields.get(0).getLevel() == 1 && findSharedRecordTypeField(rootFields.get(0)) != null) {
            return CopybookPattern.SHARED_RECORD_TYPE;
        }
        return CopybookPattern.UNKNOWN;
    }

    private void processMultiple01Levels(ParseResult result) {
        CobolField baseRecord = result.getReferenceFields().stream()
                .filter(f -> f.getLevel() == 1 && f.getRedefines() == null).findFirst().orElse(null);

        result.getReferenceFields().stream()
                .filter(f -> f.getLevel() == 1 && f.getRedefines() != null)
                .forEach(field -> {
                    RecordLayout layout = new RecordLayout(field.getName());
                    layout.setRedefines(field.getRedefines());
                    layout.setStartPosition(1);
                    layout.setLength(result.getTotalLength());
                    layout.setDescription("Layout for " + field.getName());
                    field.getChildren().forEach(child -> layout.getFields().add(deepCopy(child)));
                    result.getRecordLayouts().add(layout);
                });

        // Keep the base record as the single reference field
        if (baseRecord != null) {
            result.setReferenceFields(List.of(baseRecord));
        } else {
            result.getReferenceFields().clear();
        }
    }

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
                layout.setDescription(layoutStructure.getName() + " - identified by " + recordTypeField.getName() + " = '" + condition.getValue() + "'");

                layout.getFields().add(deepCopy(recordTypeField));
                for (CobolField child : layoutStructure.getChildren()) {
                    layout.getFields().add(deepCopy(child));
                }
                result.getRecordLayouts().add(layout);
            }
        }
        result.setReferenceFields(List.of(mainRecord));
    }

    private CobolField findSharedRecordTypeField(CobolField mainRecord) {
        return mainRecord.getChildren().stream()
                .filter(child -> child.getLevel() < 10 && !child.getConditionNames().isEmpty() && child.getRedefines() == null)
                .findFirst().orElse(null);
    }

    private CobolField findStructureForCondition(CobolField mainRecord, String conditionName) {
        String targetNamePart;
        if (conditionName.contains("HDR")) targetNamePart = "HEADER";
        else if (conditionName.contains("DTL")) targetNamePart = "DETAIL";
        else if (conditionName.contains("TRL")) targetNamePart = "TRAILER";
        else return null;

        return mainRecord.getChildren().stream()
                .filter(child -> child.getName().contains(targetNamePart))
                .findFirst().orElse(null);
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
        original.getArrayElements().forEach(ae -> copy.getArrayElements().add(ae));
        original.getChildren().forEach(child -> copy.addChild(deepCopy(child)));

        return copy;
    }
}

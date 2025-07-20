package com.cobol.parser.processor;

import com.cobol.parser.model.*;
import java.util.ArrayList;
import java.util.List;

public class LayoutProcessor implements AstProcessor {

    private enum CopybookPattern {
        MULTIPLE_01_LEVEL_REDEFINES,
        SHARED_RECORD_TYPE,
        UNKNOWN
    }

    @Override
    public void process(ParseResult parseResult) {
        CopybookPattern pattern = detectPattern(parseResult.getReferenceFields());
        CobolField referenceRecord = findBaseRecord(parseResult.getReferenceFields());

        if (referenceRecord == null && !parseResult.getReferenceFields().isEmpty()) {
            referenceRecord = parseResult.getReferenceFields().get(0);
        }
        if (referenceRecord == null) return;

        List<CobolField> allRootFields = new ArrayList<>(parseResult.getReferenceFields());
        parseResult.setReferenceFields(List.of(referenceRecord));

        switch (pattern) {
            case MULTIPLE_01_LEVEL_REDEFINES:
                processMultiple01Levels(parseResult, allRootFields);
                break;
            case SHARED_RECORD_TYPE:
                processSharedRecordType(parseResult, referenceRecord);
                break;
            default:
                break;
        }
    }

    private CopybookPattern detectPattern(List<CobolField> rootFields) {
        if (rootFields.isEmpty()) return CopybookPattern.UNKNOWN;

        long count01Redefines = rootFields.stream()
                .filter(f -> f.getLevel() == 1 && f.getRedefines() != null)
                .count();
        if (count01Redefines > 0) {
            return CopybookPattern.MULTIPLE_01_LEVEL_REDEFINES;
        }

        if (!rootFields.isEmpty() && findSharedRecordTypeField(rootFields.get(0)) != null) {
            return CopybookPattern.SHARED_RECORD_TYPE;
        }

        return CopybookPattern.UNKNOWN;
    }

    private void processMultiple01Levels(ParseResult result, List<CobolField> allRootFields) {
        for (CobolField field : allRootFields) {
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
    }

    private void processSharedRecordType(ParseResult result, CobolField mainRecord) {
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
    }

    private CobolField findBaseRecord(List<CobolField> rootFields) {
        return rootFields.stream()
                .filter(f -> f.getLevel() == 1 && f.getRedefines() == null)
                .findFirst()
                .orElse(null);
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

        original.getConditionNames().forEach(cn -> copy.getConditionNames().add(new ConditionName(cn.getName(), cn.getValue())));
        original.getArrayElements().forEach(ae -> copy.getArrayElements().add(ae));
        original.getChildren().forEach(child -> copy.addChild(deepCopy(child)));

        return copy;
    }
}

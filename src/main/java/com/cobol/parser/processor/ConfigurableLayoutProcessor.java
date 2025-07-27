package com.cobol.parser.processor;

import com.cobol.parser.model.CobolField;
import com.cobol.parser.model.ConditionName;
import com.cobol.parser.model.ParseResult;
import com.cobol.parser.model.RecordLayout;
import com.cobol.rules.LayoutMapping;
import com.cobol.rules.Rule;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ConfigurableLayoutProcessor {

    public void process(ParseResult parseResult, Rule rule) {
        Map<String, CobolField> rootFieldMap = new HashMap<>();
        for (CobolField root : parseResult.getReferenceFields()) {
            if (root.getLevel() == 1) {
                rootFieldMap.put(root.getName(), root);
            }
        }

        Optional<CobolField> mainContainer = findMainContainer(parseResult.getReferenceFields());

        for (LayoutMapping mapping : rule.getLayouts()) {
            CobolField sourceRecord = findSourceRecord(rootFieldMap, mainContainer, mapping);

            if (sourceRecord != null) {
                RecordLayout layout = new RecordLayout(mapping.getLayoutName());
                layout.setDescription("Layout for '" + mapping.getLayoutName() + "' defined by rule '" + rule.getId() + "'");

                int layoutLength = parseResult.getTotalLength() > 0 ? parseResult.getTotalLength() : sourceRecord.getLength();
                layout.setLength(layoutLength);

                if (mainContainer.isPresent() && !mainContainer.get().equals(sourceRecord)) {
                    layout.setStartPosition(sourceRecord.getStartPosition());
                    layout.setEndPosition(sourceRecord.getEndPosition());
                } else {
                    layout.setStartPosition(1);
                    layout.setEndPosition(layoutLength);
                }

                addIdentificationCriteria(layout, rule, mapping);

                mainContainer.ifPresent(container -> {
                    findSharedRecordTypeField(container).ifPresent(typeField -> layout.getFields().add(deepCopy(typeField)));
                });

                for (CobolField child : sourceRecord.getChildren()) {
                    layout.getFields().add(deepCopy(child));
                }
                parseResult.getRecordLayouts().add(layout);
            }
        }
        parseResult.getReferenceFields().clear();
    }

    private Optional<CobolField> findMainContainer(java.util.List<CobolField> rootFields) {
        return rootFields.stream().filter(rf -> rf.getChildren().stream().anyMatch(child -> child.getRedefines() != null)).findFirst();
    }

    private Optional<CobolField> findSharedRecordTypeField(CobolField mainContainer) {
        return mainContainer.getChildren().stream()
                .filter(child -> !child.getConditionNames().isEmpty() && child.getRedefines() == null)
                .findFirst();
    }

    private CobolField findSourceRecord(Map<String, CobolField> rootFieldMap, Optional<CobolField> mainContainer, LayoutMapping mapping) {
        if (mapping.getCopybookRecordName() != null) {
            if (rootFieldMap.containsKey(mapping.getCopybookRecordName())) {
                return rootFieldMap.get(mapping.getCopybookRecordName());
            }
            if (mainContainer.isPresent()) {
                for(CobolField child : mainContainer.get().getChildren()){
                    if(child.getName().equals(mapping.getCopybookRecordName())){
                        return child;
                    }
                }
            }
        }
        return rootFieldMap.values().stream().findFirst().orElse(null);
    }

    private void addIdentificationCriteria(RecordLayout layout, Rule rule, LayoutMapping mapping) {
        layout.getIdentificationCriteria().put("type", rule.getIdentifier().getType());
        if ("FIELD_VALUE_MATCH".equals(rule.getIdentifier().getType())) {
            layout.getIdentificationCriteria().put("position", String.valueOf(rule.getIdentifier().getPosition()));
            layout.getIdentificationCriteria().put("length", String.valueOf(rule.getIdentifier().getLength()));
            layout.getIdentificationCriteria().put("expectedValue", mapping.getValue());
        } else if ("SPEL_CONDITION".equals(rule.getIdentifier().getType())) {
            layout.getIdentificationCriteria().put("condition", mapping.getCondition());
        }
    }

    private CobolField deepCopy(CobolField original) {
        if (original == null) return null;
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
        if (original.getConditionNames() != null) {
            for (ConditionName cn : original.getConditionNames()) {
                copy.getConditionNames().add(new ConditionName(cn.getName(), cn.getValue()));
            }
        }
        if (original.getChildren() != null) {
            for (CobolField child : original.getChildren()) {
                copy.addChild(deepCopy(child));
            }
        }
        return copy;
    }
}

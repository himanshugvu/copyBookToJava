package com.cobol.parser.processor;

import com.cobol.parser.model.*;

import java.util.List;

public class OccursProcessor implements AstProcessor {
    @Override
    public void process(ParseResult parseResult) {
        for (RecordLayout layout : parseResult.getRecordLayouts()) {
            expandOccursInFields(layout.getFields());
        }
    }
    private void expandOccursInFields(List<CobolField> fields) {
        if (fields == null) return;
        for (CobolField field : fields) {
            if (field.getOccursCount() > 0 && !field.getChildren().isEmpty()) {
                int singleOccurrenceLength = field.getLength() / field.getOccursCount();
                int currentPos = field.getStartPosition();
                for (int i = 1; i <= field.getOccursCount(); i++) {
                    ArrayElement arrayElement = new ArrayElement(i, currentPos, singleOccurrenceLength);
                    populateArrayElementFields(field.getChildren(), arrayElement, currentPos);
                    field.getArrayElements().add(arrayElement);
                    currentPos += singleOccurrenceLength;
                }
                field.getChildren().clear();
            } else {
                expandOccursInFields(field.getChildren());
            }
        }
    }
    private void populateArrayElementFields(List<CobolField> children, ArrayElement arrayElement, int basePosition) {
        int currentPos = basePosition;
        for (CobolField child : children) {
            int fieldLength = child.getLength();
            FieldPosition fieldPos = new FieldPosition(child.getName(), currentPos, currentPos + fieldLength - 1, fieldLength, child.getPicture(), child.getDataType(), child.getUsage());
            arrayElement.getFields().add(fieldPos);
            currentPos += fieldLength;
        }
    }
}

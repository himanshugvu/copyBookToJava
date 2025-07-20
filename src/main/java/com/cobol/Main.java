package com.cobol;

import com.cobol.parser.CopybookParserFacade;
import com.cobol.parser.model.ParseResult;
import com.cobol.parser.util.FileUtils;
import com.cobol.parser.util.JsonUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        try {
            String file = "employee-record.cbl";
            Path copybookPath = Paths.get(file);
            String outputFile = FileUtils.removeExtension(file) + ".json";
            CopybookParserFacade parser = new CopybookParserFacade();
            ParseResult result = parser.parse(copybookPath);
            String jsonOutput = JsonUtils.toPrettyJson(result);
            FileUtils.writeJsonFile(jsonOutput, outputFile);
            System.out.println(jsonOutput);
        } catch (Exception e) {
            System.err.println("Error parsing copybook: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

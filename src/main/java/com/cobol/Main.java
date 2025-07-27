package com.cobol;

import com.cobol.parser.CopybookParserFacade;
import com.cobol.parser.model.ParseResult;
import com.cobol.parser.util.JsonUtils;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar parser.jar <path-to-copybook-file>");
            System.exit(1);
        }

        try {
            // The facade is now instantiated directly without any rule configurations.
            CopybookParserFacade parser = new CopybookParserFacade();

            // The command-line argument is treated as a direct file path.
            ParseResult result = parser.parse(Paths.get(args[0]));

            String jsonOutput = JsonUtils.toPrettyJson(result);
            System.out.println(jsonOutput);

        } catch (Exception e) {
            System.err.println("Error parsing copybook: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

package com.cobol.parser;

import com.cobol.parser.model.ParseResult;
import com.cobol.parser.processor.LayoutProcessor;
import com.cobol.parser.processor.OccursProcessor;
import com.cobol.parser.processor.PositionProcessor;
import com.cobol.parser.token.CobolToken;
import com.cobol.parser.token.Tokenizer;
import com.cobol.parser.util.FileUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CopybookParserFacade {

    private final Tokenizer tokenizer;
    private final AstBuilder astBuilder;
    private final PositionProcessor positionProcessor;
    private final LayoutProcessor layoutProcessor;
    private final OccursProcessor occursProcessor;

    public CopybookParserFacade() {
        this.tokenizer = new Tokenizer();
        this.astBuilder = new AstBuilder();
        this.positionProcessor = new PositionProcessor();
        this.layoutProcessor = new LayoutProcessor();
        this.occursProcessor = new OccursProcessor();
    }

    /**
     * Parses a COBOL copybook file directly from its path.
     * This method orchestrates the entire parsing and processing pipeline.
     *
     * @param copybookPath The direct path to the .cbl file.
     * @return A ParseResult object containing the structured layouts.
     * @throws IOException If the file cannot be read.
     */
    public ParseResult parse(Path copybookPath) throws IOException {
        List<String> lines = FileUtils.readLines(copybookPath);
        List<CobolToken> tokens = tokenizer.tokenize(lines);

        int recordLength = extractRecordLength(lines, tokens);

        // Build the initial AST from tokens.
        ParseResult result = astBuilder.build(tokens);
        result.setFileName(copybookPath.getFileName().toString());
        result.setTotalLength(recordLength);

        // --- Processing Pipeline ---
        // 1. Calculate field positions and lengths.
        positionProcessor.process(result);
        // 2. Identify copybook patterns and create record layouts.
        layoutProcessor.process(result);
        // 3. Expand OCCURS clauses into array structures.
        occursProcessor.process(result);

        return result;
    }

    /**
     * Intelligently extracts the record length from the copybook.
     * It first checks for a "* REC LEN" comment, then for a base 01-level PIC clause.
     */
    private int extractRecordLength(List<String> lines, List<CobolToken> tokens) {
        // Priority 1: Check for comments like "* REC LEN : 300"
        Pattern commentPattern = Pattern.compile("^\\*.*REC\\s+LEN\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        for (String line : lines) {
            Matcher matcher = commentPattern.matcher(line.trim());
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }

        // Priority 2: Find the base 01-level record and parse its PIC clause, e.g., "PIC X(250)".
        for (CobolToken token : tokens) {
            if (token.getLevel() == 1 && token.getRedefines() == null && token.getPicture() != null) {
                Pattern picPattern = Pattern.compile("[Xx]\\((\\d+)\\)");
                Matcher matcher = picPattern.matcher(token.getPicture());
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
        }

        return 300; // Final fallback default
    }
}

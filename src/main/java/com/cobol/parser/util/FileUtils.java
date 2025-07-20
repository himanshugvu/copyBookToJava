package com.cobol.parser.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FileUtils {
    public static List<String> readLines(Path path) throws IOException {
        return Files.readAllLines(path);
    }
    public static String removeExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return lastDot == -1 ? fileName : fileName.substring(0, lastDot);
    }
    public static void writeJsonFile(String json, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        Files.writeString(path, json);
    }
}

package output;

import calculator.MetricsCalculator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ResultPrinter {

    private static final Logger logger = LogManager.getLogger(ResultPrinter.class);

    private ResultPrinter() {
    }

    private static final String[] HEADERS = {"Name",
            "WMC", "DIT", "NOCC", "CBO", "RFC", "LCOM",
            "WMC*", "NOM", "MPC", "DAC", "SIZE1", "SIZE2", "DSC", "NOH", "ANA", "DAM", "DCC", "CAM", "MOA", "MFA", "NOP", "CIS", "NPM",
            "Reusability", "Flexibility", "Understandability", "Functionality", "Extendibility", "Effectiveness",
            "fanIn", "ClassNames"};


    public static boolean printCSV(MetricsCalculator mc, String fullOutFilePath) {
        StringBuilder output = new StringBuilder();
        appendHeaders(output, "\t");
        output.replace(output.lastIndexOf("\t"), output.lastIndexOf("\t") + 1, "\n");
        mc.getProject().getJavaFiles().forEach(javaFile -> output.append(javaFile.getPath().replace("\\", "/")).append("\t").append(javaFile.getQualityMetrics()).append("\t").append(javaFile.getClassNames()).append("\n"));

        return writeFile(fullOutFilePath, output);
    }

    public static boolean printCSV(MetricsCalculator mc, String fullOutFilePath, String delimiter) {
        StringBuilder output = new StringBuilder();
        appendHeaders(output, delimiter);
        output.replace(output.lastIndexOf(delimiter), output.lastIndexOf(delimiter) + 1, "\n");
        mc.getProject().getJavaFiles().forEach(javaFile -> output.append(javaFile.getPath().replace("\\", "/")).append(delimiter).append(javaFile.getQualityMetrics().toString(delimiter)).append(delimiter).append(javaFile.getClassNames()).append("\n"));

        return writeFile(fullOutFilePath, output);
    }

    public static boolean printString(MetricsCalculator mc) {
        StringBuilder output = new StringBuilder();
        appendHeaders(output, "\t");
        output.replace(output.lastIndexOf("\t"), output.lastIndexOf("\t") + 1, "\n");
        mc.getProject().getJavaFiles().forEach(javaFile -> output.append(javaFile.getPath().replace("\\", "/")).append("\t").append(javaFile.getQualityMetrics()).append("\t").append(javaFile.getClassNames()).append("\n"));

        logger.info(output);
        return true;
    }

    private static void appendHeaders(StringBuilder data, String delimiter) {
        for (String header : HEADERS)
            data.append(header).append(delimiter);
    }

    private static boolean writeFile(String path, StringBuilder data) {
        try {
            Files.write(Paths.get(path), data.toString().getBytes());
        } catch (IOException e) {
            logger.error("Exception was thrown: {}", e.getMessage());
            return false;
        }
        return true;
    }

}

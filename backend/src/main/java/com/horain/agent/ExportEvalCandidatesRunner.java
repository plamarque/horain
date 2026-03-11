package com.horain.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * CLI export of eval candidates when profile "export" is active.
 * Prefer using the prod endpoint GET /admin/export-eval-candidates from the script
 * so the local env is never pointed at prod DB.
 */
@Component
@Order(Integer.MAX_VALUE)
@Conditional(ExportProfileCondition.class)
public class ExportEvalCandidatesRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ExportEvalCandidatesRunner.class);

    private final ExportEvalCandidatesService exportEvalCandidatesService;

    public ExportEvalCandidatesRunner(ExportEvalCandidatesService exportEvalCandidatesService) {
        this.exportEvalCandidatesService = exportEvalCandidatesService;
    }

    @Override
    public void run(String... args) throws Exception {
        String outputPath = null;
        for (String arg : args) {
            if (arg.startsWith("--export.output=")) {
                outputPath = arg.substring("--export.output=".length()).trim();
                break;
            }
        }

        List<Map<String, Object>> rows = exportEvalCandidatesService.getCandidates();
        var mapper = exportEvalCandidatesService.getObjectMapper();
        try (BufferedWriter writer = outputPath != null && !outputPath.isBlank()
                ? Files.newBufferedWriter(Path.of(outputPath), StandardCharsets.UTF_8)
                : new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            for (Map<String, Object> row : rows) {
                writer.write(mapper.writeValueAsString(row));
                writer.newLine();
            }
        }
        log.info("Exported {} eval candidates to {}", rows.size(), outputPath != null ? outputPath : "stdout");
        System.exit(0);
    }
}

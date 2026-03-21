package com.horain.agent

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Conditional
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * CLI export of eval candidates when profile "export" is active.
 * Prefer using the prod endpoint GET /admin/export-eval-candidates from the script
 * so the local env is never pointed at prod DB.
 */
@Component
@Order(Int.MAX_VALUE)
@Conditional(ExportProfileCondition::class)
class ExportEvalCandidatesRunner(
    private val exportEvalCandidatesService: ExportEvalCandidatesService
) : CommandLineRunner {

    override fun run(vararg args: String) {
        var outputPath: String? = null
        for (arg in args) {
            if (arg.startsWith("--export.output=")) {
                outputPath = arg.substring("--export.output=".length).trim()
                break
            }
        }
        val rows = exportEvalCandidatesService.getCandidates()
        val mapper = exportEvalCandidatesService.objectMapper
        val writer: BufferedWriter = if (!outputPath.isNullOrBlank()) {
            Files.newBufferedWriter(Path.of(outputPath), StandardCharsets.UTF_8)
        } else {
            BufferedWriter(OutputStreamWriter(System.out, StandardCharsets.UTF_8))
        }
        writer.use { w ->
            for (row in rows) {
                w.write(mapper.writeValueAsString(row))
                w.newLine()
            }
        }
        log.info("Exported {} eval candidates to {}", rows.size, outputPath ?: "stdout")
        System.exit(0)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ExportEvalCandidatesRunner::class.java)
    }
}

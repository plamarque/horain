package com.horain.dev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Dev-only endpoint to load fictional seed data.
 * Disabled when horain.dev.seed-enabled is false (e.g. production).
 * Optional body: { "fixedToday": "2025-03-10" } for deterministic evals.
 */
@RestController
@RequestMapping("/dev")
public class DevSeedController {

    private final DevSeedService devSeedService;

    @Value("${horain.dev.seed-enabled:false}")
    private boolean seedEnabled;

    public DevSeedController(DevSeedService devSeedService) {
        this.devSeedService = devSeedService;
    }

    @PostMapping("/seed")
    public ResponseEntity<?> loadSeed(@RequestBody(required = false) SeedRequest body) {
        if (!seedEnabled) {
            return ResponseEntity.notFound().build();
        }
        LocalDate fixedToday = null;
        if (body != null && body.fixedToday() != null && !body.fixedToday().isBlank()) {
            try {
                fixedToday = LocalDate.parse(body.fixedToday());
            } catch (Exception ignored) {
                // Ignore invalid date, use now
            }
        }
        DevSeedService.DevSeedResult result = devSeedService.loadSeed(fixedToday);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/seed/reset")
    public ResponseEntity<?> resetSeed(@RequestBody(required = false) SeedRequest body) {
        if (!seedEnabled) {
            return ResponseEntity.notFound().build();
        }
        LocalDate fixedToday = null;
        if (body != null && body.fixedToday() != null && !body.fixedToday().isBlank()) {
            try {
                fixedToday = LocalDate.parse(body.fixedToday());
            } catch (Exception ignored) {
                // Ignore invalid date, use now
            }
        }
        DevSeedService.DevSeedResult result = devSeedService.resetAndLoadSeed(fixedToday);
        return ResponseEntity.ok(result);
    }

    public record SeedRequest(String fixedToday) {}
}

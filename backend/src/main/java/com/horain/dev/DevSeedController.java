package com.horain.dev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dev-only endpoint to load fictional seed data.
 * Disabled when horain.dev.seed-enabled is false (e.g. production).
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
    public ResponseEntity<?> loadSeed() {
        if (!seedEnabled) {
            return ResponseEntity.notFound().build();
        }
        DevSeedService.DevSeedResult result = devSeedService.loadSeed();
        return ResponseEntity.ok(result);
    }
}

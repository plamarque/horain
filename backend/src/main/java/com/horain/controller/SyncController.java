package com.horain.controller;

import com.horain.dto.SyncPullResponse;
import com.horain.dto.SyncPushRequest;
import com.horain.dto.SyncPushResponse;
import com.horain.sync.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Sync API controller.
 * POST /sync/push - accepts batch of operations
 * GET /sync/pull - returns server updates since timestamp
 */
@RestController
@RequestMapping("/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/push")
    public ResponseEntity<SyncPushResponse> push(@RequestBody SyncPushRequest request) {
        SyncPushResponse response = syncService.push(request.getOperations());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pull")
    public ResponseEntity<SyncPullResponse> pull(
            @RequestParam(value = "since", required = false) Long since) {
        SyncPullResponse response = syncService.pull(since);
        return ResponseEntity.ok(response);
    }
}

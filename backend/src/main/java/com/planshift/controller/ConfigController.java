package com.planshift.controller;

import com.planshift.experiment.KeptIndexService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** What this server allows, so the UI only offers what will work. */
@RestController
public class ConfigController {

    private final KeptIndexService keptIndexService;

    public ConfigController(KeptIndexService keptIndexService) {
        this.keptIndexService = keptIndexService;
    }

    @GetMapping("/api/config")
    public Map<String, Boolean> config() {
        return Map.of("keepIndexEnabled", keptIndexService.enabled());
    }
}

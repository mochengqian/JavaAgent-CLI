package com.javagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsDefaultsAndPersistsChanges() throws IOException {
        Config config = Config.load(tempDir);

        assertTrue(config.isMockMode());
        assertTrue(config.autoSave());
        assertEquals("gpt-5.4-mini", config.model());
        assertEquals(12, config.maxIterations());
        assertFalse(config.bashEnabled());
        assertTrue(config.streamResponses());
        assertTrue(config.approvalCacheEnabled());
        assertFalse(config.allowExternalPaths());
        assertTrue(Files.exists(config.configPath()));

        config.setMockMode(false);
        config.setApiKey("sk-test");
        config.setBashEnabled(true);
        config.setCustomSystemPrompt("You are in demo mode.");
        config.setStreamResponses(false);

        Config reloaded = Config.load(tempDir);
        assertFalse(reloaded.isMockMode());
        assertEquals("sk-test", reloaded.apiKey());
        assertTrue(reloaded.bashEnabled());
        assertEquals("You are in demo mode.", reloaded.customSystemPrompt());
        assertFalse(reloaded.streamResponses());
    }
}

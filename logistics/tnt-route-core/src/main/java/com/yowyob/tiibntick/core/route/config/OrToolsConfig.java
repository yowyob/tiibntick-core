package com.yowyob.tiibntick.core.route.config;

import com.google.ortools.Loader;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrToolsConfig {

    @PostConstruct
    public void loadOrToolsNative() {
        try {
            Loader.loadNativeLibraries();
        } catch (Exception e) {
            System.err.println("[OrToolsConfig] Failed to load OR-Tools native libraries: " + e.getMessage());
        }
    }
}

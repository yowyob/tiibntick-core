package com.yowyob.tiibntick.core.realtime.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Registers HTTP REST adapters (e.g. GPS ping) for platform backends such as tnt-agency.
 */
@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.realtime.adapter.in.rest")
public class RealtimeRestAdapterConfig {
}

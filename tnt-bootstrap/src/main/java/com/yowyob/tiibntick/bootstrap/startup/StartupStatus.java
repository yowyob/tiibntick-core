package com.yowyob.tiibntick.bootstrap.startup;

/**
 * Overall startup status of TiiBnTick Core.
 *
 * @author MANFOUO Braun
 */
public enum StartupStatus {
    /** Context is being initialized — before any startup step runs. */
    INITIALIZING,
    /** At least one startup step is running. */
    IN_PROGRESS,
    /** All startup steps completed successfully. */
    COMPLETED,
    /** A critical startup step failed — application is not operational. */
    FAILED,
    /** All mandatory steps completed but some optional steps failed. */
    DEGRADED
}

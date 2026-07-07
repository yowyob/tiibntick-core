package com.yowyob.tiibntick.bootstrap.startup;

/**
 * Status of an individual startup step within {@link TntStartupSequence}.
 *
 * @author MANFOUO Braun
 */
public enum StepStatus {
    PENDING,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    SKIPPED
}

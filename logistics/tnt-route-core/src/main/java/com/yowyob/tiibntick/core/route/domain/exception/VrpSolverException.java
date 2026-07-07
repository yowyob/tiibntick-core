package com.yowyob.tiibntick.core.route.domain.exception;

public class VrpSolverException extends RuntimeException {
    public VrpSolverException(String message) { super(message); }
    public VrpSolverException(String message, Throwable cause) { super(message, cause); }
}

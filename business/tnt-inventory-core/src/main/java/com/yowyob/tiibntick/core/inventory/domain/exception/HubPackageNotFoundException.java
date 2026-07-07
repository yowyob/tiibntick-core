package com.yowyob.tiibntick.core.inventory.domain.exception;
import java.util.UUID;
public class HubPackageNotFoundException extends RuntimeException {
    public HubPackageNotFoundException(UUID packageId) { super("Hub package not found: " + packageId); }
    public HubPackageNotFoundException(String trackingCode) { super("Hub package not found with tracking: " + trackingCode); }
}

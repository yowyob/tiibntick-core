package com.yowyob.tiibntick.core.resource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tnt.resource")
public class ResourceCoreProperties {

    private Kafka kafka = new Kafka();
    private Redis redis = new Redis();
    private Maintenance maintenance = new Maintenance();

    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }

    public Redis getRedis() { return redis; }
    public void setRedis(Redis redis) { this.redis = redis; }

    public Maintenance getMaintenance() { return maintenance; }
    public void setMaintenance(Maintenance maintenance) { this.maintenance = maintenance; }

    public static class Kafka {
        private String vehicleRegisteredTopic = "tnt.resource.vehicle.registered";
        private String vehicleAssignedTopic = "tnt.resource.vehicle.assigned";
        private String vehicleUnassignedTopic = "tnt.resource.vehicle.unassigned";
        private String vehicleMaintenanceTopic = "tnt.resource.vehicle.maintenance";
        private String vehicleRetiredTopic = "tnt.resource.vehicle.retired";
        private String vehicleLocationTopic = "tnt.resource.vehicle.location";
        private String maintenanceAlertTopic = "tnt.resource.maintenance.alert";
        private String equipmentAssignedTopic = "tnt.resource.equipment.assigned";

        public String getVehicleRegisteredTopic() { return vehicleRegisteredTopic; }
        public void setVehicleRegisteredTopic(String vehicleRegisteredTopic) { this.vehicleRegisteredTopic = vehicleRegisteredTopic; }
        public String getVehicleAssignedTopic() { return vehicleAssignedTopic; }
        public void setVehicleAssignedTopic(String vehicleAssignedTopic) { this.vehicleAssignedTopic = vehicleAssignedTopic; }
        public String getVehicleUnassignedTopic() { return vehicleUnassignedTopic; }
        public void setVehicleUnassignedTopic(String vehicleUnassignedTopic) { this.vehicleUnassignedTopic = vehicleUnassignedTopic; }
        public String getVehicleMaintenanceTopic() { return vehicleMaintenanceTopic; }
        public void setVehicleMaintenanceTopic(String vehicleMaintenanceTopic) { this.vehicleMaintenanceTopic = vehicleMaintenanceTopic; }
        public String getVehicleRetiredTopic() { return vehicleRetiredTopic; }
        public void setVehicleRetiredTopic(String vehicleRetiredTopic) { this.vehicleRetiredTopic = vehicleRetiredTopic; }
        public String getVehicleLocationTopic() { return vehicleLocationTopic; }
        public void setVehicleLocationTopic(String vehicleLocationTopic) { this.vehicleLocationTopic = vehicleLocationTopic; }
        public String getMaintenanceAlertTopic() { return maintenanceAlertTopic; }
        public void setMaintenanceAlertTopic(String maintenanceAlertTopic) { this.maintenanceAlertTopic = maintenanceAlertTopic; }
        public String getEquipmentAssignedTopic() { return equipmentAssignedTopic; }
        public void setEquipmentAssignedTopic(String equipmentAssignedTopic) { this.equipmentAssignedTopic = equipmentAssignedTopic; }
    }

    public static class Redis {
        private long vehicleLocationTtlSeconds = 3600L;
        private String vehicleLocationKeyPrefix = "tnt:vehicle:location:";

        public long getVehicleLocationTtlSeconds() { return vehicleLocationTtlSeconds; }
        public void setVehicleLocationTtlSeconds(long vehicleLocationTtlSeconds) { this.vehicleLocationTtlSeconds = vehicleLocationTtlSeconds; }
        public String getVehicleLocationKeyPrefix() { return vehicleLocationKeyPrefix; }
        public void setVehicleLocationKeyPrefix(String vehicleLocationKeyPrefix) { this.vehicleLocationKeyPrefix = vehicleLocationKeyPrefix; }
    }

    public static class Maintenance {
        private int alertDaysBeforeDue = 7;
        private double alertKmBeforeDue = 500.0;

        public int getAlertDaysBeforeDue() { return alertDaysBeforeDue; }
        public void setAlertDaysBeforeDue(int alertDaysBeforeDue) { this.alertDaysBeforeDue = alertDaysBeforeDue; }
        public double getAlertKmBeforeDue() { return alertKmBeforeDue; }
        public void setAlertKmBeforeDue(double alertKmBeforeDue) { this.alertKmBeforeDue = alertKmBeforeDue; }
    }
}

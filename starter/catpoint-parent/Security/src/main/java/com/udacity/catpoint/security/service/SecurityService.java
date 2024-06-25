package com.udacity.catpoint.security.service;

import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.AlarmStatus;
import com.udacity.catpoint.security.data.ArmingStatus;
import com.udacity.catpoint.security.data.SecurityRepository;
import com.udacity.catpoint.security.data.Sensor;
import com.udacity.catpoint.image.service.ImageService;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 * <p>
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private final ImageService imageServiceInit;
    private final SecurityRepository securityRepo;
    private final Set<StatusListener> statusListenerHashSet = new HashSet<>();
    private Boolean catDetector = false;

    public SecurityService(SecurityRepository securityRepo, ImageService imageServiceInit) {
        this.securityRepo = securityRepo;
        this.imageServiceInit = imageServiceInit;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     *
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        // Trigger alarm if the system is armed and a cat is detected
        if (Boolean.TRUE.equals(catDetector) && armingStatus == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        }

        // Set alarm status to NO_ALARM if the system is disarmed
        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            // If the system is armed, deactivate all sensors
            for (Sensor sensor : getSensors()) {
                changeSensorActivationStatus(sensor, false);
            }
        }

        // Update the arming status in the security repository
        securityRepo.setArmingStatus(armingStatus);

        // Notify all status listeners
        for (StatusListener sl : statusListenerHashSet) {
            sl.sensorStatusChanged();
        }
    }

    private boolean getAllSensorsWithStatus(boolean status) {
        return getSensors().stream().anyMatch(sensor -> sensor.getActive() == status);
    }


    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     *
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        catDetector = cat;

        if (Boolean.TRUE.equals(cat) && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (cat != null && Boolean.TRUE.equals(!cat) && getAllSensorsWithStatus(false)) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        statusListenerHashSet.forEach(sl -> sl.catDetected(Boolean.TRUE.equals(cat)));
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     *
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListenerHashSet.add(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     *
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepo.setAlarmStatus(status);
        statusListenerHashSet.forEach(sl -> sl.notify(status));
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if (securityRepo.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }
        switch (securityRepo.getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated() {
        switch (securityRepo.getAlarmStatus()) {
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.NO_ALARM);
            case ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     *
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        AlarmStatus alarmStatus = securityRepo.getAlarmStatus();

        if (alarmStatus != AlarmStatus.ALARM) {
            boolean currentActiveStatus = sensor.getActive();

            if (Boolean.TRUE.equals(active) && !currentActiveStatus) {
                handleSensorActivated();
            } else if (active != null && Boolean.TRUE.equals(!active) && currentActiveStatus) {
                handleSensorDeactivated();
            }

            sensor.setActive(active);
            securityRepo.updateSensor(sensor);
        }
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     *
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageServiceInit.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepo.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepo.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepo.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepo.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepo.getArmingStatus();
    }
}

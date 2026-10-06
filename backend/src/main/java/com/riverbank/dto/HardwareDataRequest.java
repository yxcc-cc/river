package com.riverbank.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class HardwareDataRequest {
    private String deviceId;
    private BigDecimal waterLevel;
    private Boolean humanDetected;
    private BigDecimal batteryVoltage;
    private String timestamp;
}

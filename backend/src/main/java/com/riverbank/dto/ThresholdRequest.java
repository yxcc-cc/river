package com.riverbank.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ThresholdRequest {
    @NotNull(message = "阈值不能为空")
    private BigDecimal waterThreshold;
}

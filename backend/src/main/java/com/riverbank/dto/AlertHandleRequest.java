package com.riverbank.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlertHandleRequest {
    @NotBlank(message = "处置备注不能为空")
    private String handleNote;
}

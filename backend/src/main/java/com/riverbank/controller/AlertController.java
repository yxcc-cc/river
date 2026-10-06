package com.riverbank.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.common.PageResult;
import com.riverbank.common.Result;
import com.riverbank.dto.AlertHandleRequest;
import com.riverbank.entity.AlertEvent;
import com.riverbank.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public Result<PageResult<AlertEvent>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<AlertEvent> alerts = alertService.listAlerts(status, deviceId, page, size);
        return Result.success(PageResult.of(alerts));
    }

    @PutMapping("/{id}/handle")
    public Result<?> handle(@PathVariable Long id,
                            @Valid @RequestBody AlertHandleRequest request,
                            Authentication authentication) {
        Integer handlerId = (Integer) authentication.getPrincipal();
        alertService.handleAlert(id, handlerId, request.getHandleNote());
        return Result.success();
    }

    @GetMapping("/count-unhandled")
    public Result<Long> countUnhandled() {
        return Result.success(alertService.countUnhandled());
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        alertService.deleteAlert(id);
        return Result.success();
    }

    @DeleteMapping("/clear")
    public Result<?> clear(@RequestParam(required = false) String status) {
        alertService.clearAlerts(status);
        return Result.success();
    }
}

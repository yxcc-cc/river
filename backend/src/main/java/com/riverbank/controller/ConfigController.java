package com.riverbank.controller;

import com.riverbank.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 运行期配置：告诉前端「视频流现在在哪个地址」。
 *
 * 摄像头在家里局域网，公网页面直接访问不到，需要家里跑隧道（cloudflared / frp）。
 * 隧道地址每次重启都可能变，所以不能写死在前端代码里 ——
 * 改由后端下发，前端打开页面时问一次就行，不用重新打包部署。
 *
 * 配置项：application.yml 的 stream.public-url
 * 留空 = 前端回退到同源 /stream（本地开发时由 Vite 代理转 8083）。
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${stream.public-url:}")
    private String streamPublicUrl;

    @GetMapping("/stream")
    public Result<Map<String, Object>> stream() {
        Map<String, Object> data = new HashMap<>();
        data.put("url", streamPublicUrl == null ? "" : streamPublicUrl.trim());
        return Result.success(data);
    }
}

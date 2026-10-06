package com.riverbank.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.common.PageResult;
import com.riverbank.common.Result;
import com.riverbank.entity.User;
import com.riverbank.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public Result<PageResult<User>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        Page<User> users = userService.listUsers(page, size, keyword);
        return Result.success(PageResult.of(users));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<User> getById(@PathVariable Integer id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('admin')")
    public Result<?> add(@RequestBody User user) {
        userService.addUser(user);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<?> update(@PathVariable Integer id, @RequestBody User user) {
        user.setId(id);
        userService.updateUser(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<?> delete(@PathVariable Integer id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('admin')")
    public Result<?> resetPassword(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return Result.success();
    }
}

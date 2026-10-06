package com.riverbank.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.entity.User;
import com.riverbank.mapper.UserMapper;
import com.riverbank.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Page<User> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword);
        }
        wrapper.orderByDesc(User::getCreateTime);

        Page<User> pageResult = userMapper.selectPage(new Page<>(page, size), wrapper);

        // Join role info
        pageResult.getRecords().forEach(user -> {
            user.setPassword(null); // Don't expose password
        });

        return pageResult;
    }

    @Override
    public User getById(Integer id) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    @Override
    public void addUser(User user) {
        User existing = userMapper.selectByUsername(user.getUsername());
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在: " + user.getUsername());
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
    }

    @Override
    public void updateUser(User user) {
        User existing = userMapper.selectById(user.getId());
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        // Don't update password here
        user.setPassword(null);
        userMapper.updateById(user);
    }

    @Override
    public void deleteUser(Integer id) {
        if (id == 1) {
            throw new IllegalArgumentException("不能删除默认管理员");
        }
        userMapper.deleteById(id);
    }

    @Override
    public void resetPassword(Integer id, String newPassword) {
        User user = new User();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }
}

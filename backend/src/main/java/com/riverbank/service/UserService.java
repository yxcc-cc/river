package com.riverbank.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.riverbank.entity.User;

public interface UserService {
    Page<User> listUsers(int page, int size, String keyword);
    User getById(Integer id);
    void addUser(User user);
    void updateUser(User user);
    void deleteUser(Integer id);
    void resetPassword(Integer id, String newPassword);
}

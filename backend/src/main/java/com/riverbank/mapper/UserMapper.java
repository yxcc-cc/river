package com.riverbank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.riverbank.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT u.*, r.role_name, r.role_key FROM t_user u " +
            "LEFT JOIN t_role r ON u.role_id = r.id " +
            "WHERE u.username = #{username}")
    User selectByUsername(String username);
}

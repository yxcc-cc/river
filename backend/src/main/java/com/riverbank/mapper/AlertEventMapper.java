package com.riverbank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.riverbank.entity.AlertEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    @Select("SELECT ae.*, d.location as device_location, u.real_name as handler_name " +
            "FROM t_alert_event ae " +
            "LEFT JOIN t_device d ON ae.device_id = d.id " +
            "LEFT JOIN t_user u ON ae.handler_id = u.id " +
            "WHERE ae.is_handled = 0 " +
            "ORDER BY ae.alert_time DESC LIMIT 10")
    List<AlertEvent> selectUnhandledRecent();
}

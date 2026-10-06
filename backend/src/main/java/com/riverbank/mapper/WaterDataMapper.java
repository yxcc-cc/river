package com.riverbank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.riverbank.entity.WaterData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WaterDataMapper extends BaseMapper<WaterData> {

    @Select("SELECT * FROM t_water_data WHERE device_id = #{deviceId} ORDER BY collect_time DESC LIMIT 1")
    WaterData selectLatestByDeviceId(String deviceId);
}

package com.riverbank.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.riverbank.entity.IntrusionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IntrusionRecordMapper extends BaseMapper<IntrusionRecord> {

    @Select("SELECT * FROM t_intrusion_record WHERE device_id = #{deviceId} ORDER BY detect_time DESC LIMIT 1")
    IntrusionRecord selectLatestByDeviceId(String deviceId);
}

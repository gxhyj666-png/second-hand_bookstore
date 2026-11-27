package com.textbook.loop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.textbook.loop.domain.BizMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BizMessageMapper extends BaseMapper<BizMessage> {
}

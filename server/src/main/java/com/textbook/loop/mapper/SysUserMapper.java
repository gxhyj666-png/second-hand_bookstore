package com.textbook.loop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.textbook.loop.domain.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}

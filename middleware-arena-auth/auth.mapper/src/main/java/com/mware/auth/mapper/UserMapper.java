package com.mware.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.auth.domain.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（骨架占位）。
 * <p>
 * TODO[双 token 登录]：实现登录时，把 auth.mapper 依赖引入 auth.biz，
 * 并在 application.yml 启用数据源后生效。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

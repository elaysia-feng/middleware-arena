package com.mware.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（骨架占位）。
 * <p>
 * TODO[双 token 登录]：字段与 sql/init.sql 同步；密码 BCrypt 存储。
 */
@Data
@TableName("user")
@Schema(description = "用户信息")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private LocalDateTime createdAt;
}

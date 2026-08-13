package com.mware.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体（骨架占位）。
 * <p>
 * TODO[双 token 登录]：字段与 sql/init.sql 同步；密码 BCrypt 存储。
 */
@Data
@TableName("user")
@Schema(description = "用户信息")
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    /** 会员标记：FREE / VIP；是否仍有效还要结合 vipExpireAt 实时判断。 */
    private String tier;

    /** VIP 到期时间；为空或不晚于当前时间时按 FREE 处理。 */
    private LocalDateTime vipExpireAt;

    private LocalDateTime createdAt;
}

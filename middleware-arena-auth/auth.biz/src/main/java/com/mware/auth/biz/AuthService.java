package com.mware.auth.biz;

import com.mware.auth.dto.request.LoginRequest;
import com.mware.auth.dto.request.RefreshRequest;
import com.mware.auth.dto.request.RegisterRequest;
import com.mware.auth.dto.response.LoginResponse;
import com.mware.auth.dto.response.MembershipResponse;
import com.mware.auth.dto.response.UserInfoResponse;

/**
 * 认证业务接口：双 token（access + refresh）登录闭环。
 * <p>
 * 密码一律 BCrypt 加密后落库；refreshToken 为不透明随机串存 Redis，
 * 天然支持刷新轮换与登出拉黑。
 */
public interface AuthService {

    /** 注册：校验用户名唯一 → 密码 BCrypt 加密落库 → 直接签发双 token */
    LoginResponse register(RegisterRequest request);

    /** 登录：校验用户名密码 → 签发双 token */
    LoginResponse login(LoginRequest request);

    /** 刷新：校验 refreshToken → 轮换作废旧 token → 签发新双 token */
    LoginResponse refresh(RefreshRequest request);

    /** 登出：删除 Redis 中的 refreshToken，立即失效 */
    void logout(String refreshToken);

    /** 当前用户信息：解析 accessToken 得到用户 id */
    UserInfoResponse me(String accessToken);

    /** 为当前用户模拟充值 30 天 VIP。 */
    MembershipResponse mockRecharge(String accessToken);

    /** 供内部服务按用户 ID 查询实时会员等级。 */
    MembershipResponse membership(Long userId);
}

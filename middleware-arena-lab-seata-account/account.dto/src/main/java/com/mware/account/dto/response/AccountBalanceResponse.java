package com.mware.account.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账户余额响应对象（对外暴露，仅含展示所需字段，不含 id / 审计时间）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountBalanceResponse {

    private Long userId;

    /** 账户余额（单位：分） */
    private Long balance;
}

package com.mware.account.biz;

import com.mware.account.domain.AccountBalance;
import com.mware.account.mapper.AccountBalanceMapper;
import org.springframework.stereotype.Service;

/**
 * 账户业务实现（骨架占位）。
 * <p>
 * TODO[Seata AT]：接入 account.mapper + 数据源后逐个实现。
 */
@Service
public class AccountServiceImpl implements AccountService {

    private final AccountBalanceMapper accountBalanceMapper;

    public AccountServiceImpl(AccountBalanceMapper accountBalanceMapper) {
        this.accountBalanceMapper = accountBalanceMapper;
    }

    @Override
    public void deductBalance(Long userId, Long amount) {
        // TODO[Seata AT 参与方] 实现清单（只列步骤，不写实现代码）：
        //   1. 参数校验：userId 非空、amount 非空且 > 0，非法抛 ApiException(PARAM_INVALID)
        //   2. 原子扣减，防止并发超扣（关键 SQL，务必带 where 余额条件）：
        //      UPDATE account_balance SET balance = balance - #{amount}
        //      WHERE user_id = #{userId} AND balance >= #{amount}
        //      等价写法：LambdaUpdateWrapper.setSql("balance = balance - " + amount)
        //      .eq(AccountBalance::getUserId, userId).ge(AccountBalance::getBalance, amount)
        //   3. update 受影响行数 == 0 说明余额不足，抛 ApiException(BALANCE_NOT_ENOUGH)
        //      —— 由 GlobalExceptionHandler 转 40902 响应，并触发 Seata 全局回滚
        //   4. 补扣/冲正（退款）可传负 amount 走同一方法，须与全局事务链路配合；
        //      回滚由 Seata AT undo_log 自动完成，勿手动删行
    }

    @Override
    public AccountBalance getBalance(Long userId) {
        // TODO[Seata AT 参与方] 实现清单：
        //   1. 参数校验：userId 非空，非法抛 ApiException(PARAM_INVALID)
        //   2. 按 user_id 查询：accountBalanceMapper.selectOne(
        //        new LambdaQueryWrapper<AccountBalance>().eq(AccountBalance::getUserId, userId))
        //      注意：user_id 有唯一键 uk_user_id，不能用 selectById(userId)（id 是主键，二者不等价）
        //   3. 查不到时返回 null 还是抛 ApiException(NOT_FOUND)，与 order 调用方约定保持一致；
        //      默认建议返回 null，由调用方决定是否视为余额 0
        return null;
    }
}

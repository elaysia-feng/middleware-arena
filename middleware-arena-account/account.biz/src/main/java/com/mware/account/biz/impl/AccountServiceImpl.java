package com.mware.account.biz.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mware.account.biz.AccountService;
import com.mware.account.domain.AccountBalance;
import com.mware.account.mapper.AccountBalanceMapper;
import com.mware.common.web.ApiException;
import com.mware.common.web.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账户余额业务实现。扣款使用带余额条件的单条 UPDATE，既避免并发超扣，
 * 也能被 Seata AT 记录到 {@code undo_log} 中参与全局回滚。
 */
@Service
public class AccountServiceImpl implements AccountService {

    private final AccountBalanceMapper accountBalanceMapper;

    public AccountServiceImpl(AccountBalanceMapper accountBalanceMapper) {
        this.accountBalanceMapper = accountBalanceMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductBalance(Long userId, Long amount) {
        if (userId == null || amount == null || amount <= 0) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }

        int rows = accountBalanceMapper.update(null, new LambdaUpdateWrapper<AccountBalance>()
                .eq(AccountBalance::getUserId, userId)
                .ge(AccountBalance::getBalance, amount)
                .setSql("balance = balance - " + amount));
        if (rows == 0) {
            throw new ApiException(ErrorCode.BALANCE_NOT_ENOUGH);
        }
    }

    @Override
    public AccountBalance getBalance(Long userId) {
        if (userId == null) {
            throw new ApiException(ErrorCode.PARAM_INVALID);
        }
        AccountBalance balance = accountBalanceMapper.selectOne(new LambdaQueryWrapper<AccountBalance>()
                .eq(AccountBalance::getUserId, userId));
        if (balance == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return balance;
    }
}

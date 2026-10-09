package com.mware.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mware.account.domain.AccountBalance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账户余额 Mapper（骨架占位）。
 * <p>
 * TODO[Seata AT 参与方]：接入 account.mapper 依赖到 biz 层，
 * 并在 application.yml 启用数据源后生效。
 */
@Mapper
public interface AccountBalanceMapper extends BaseMapper<AccountBalance> {
}

package com.mware.account.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 账户余额实体（骨架占位）。
 * <p>
 * TODO[Seata AT 参与方]：字段与 sql/init.sql 同步。
 */
@Data
@TableName("account_balance")
public class AccountBalance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal balance;
}

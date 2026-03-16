package com.qoder.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qoder.fund.entity.FundTransaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransactionMapper extends BaseMapper<FundTransaction> {
}

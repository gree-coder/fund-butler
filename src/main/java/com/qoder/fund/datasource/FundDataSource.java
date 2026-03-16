package com.qoder.fund.datasource;

import com.qoder.fund.dto.FundDetailDTO;
import com.qoder.fund.dto.FundSearchDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 基金数据源接口 - 定义外部数据获取能力
 */
public interface FundDataSource {

    /**
     * 搜索基金
     */
    List<FundSearchDTO> searchFund(String keyword);

    /**
     * 获取基金详情
     */
    FundDetailDTO getFundDetail(String fundCode);

    /**
     * 获取历史净值
     * @param fundCode 基金代码
     * @param startDate 起始日期 yyyy-MM-dd
     * @param endDate 结束日期 yyyy-MM-dd
     * @return 净值列表 [{navDate, nav, accNav, dailyReturn}]
     */
    List<Map<String, Object>> getNavHistory(String fundCode, String startDate, String endDate);

    /**
     * 获取实时估值
     * @return {estimateNav, estimateReturn, estimateTime}
     */
    Map<String, Object> getEstimateNav(String fundCode);

    /**
     * 数据源名称
     */
    String getName();
}

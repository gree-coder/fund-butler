package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class WatchlistDTO {
    private Long id;
    private String fundCode;
    private String fundName;
    private String groupName;
    private BigDecimal latestNav;
    private BigDecimal estimateReturn;
    private BigDecimal actualNav;
    private BigDecimal actualReturn;
    /** 实际涨幅是否为延迟数据(QDII T+1) */
    private Boolean actualReturnDelayed;
    private Map<String, BigDecimal> performance;
    /** 智能预估涨跌幅 */
    private BigDecimal smartEstimateReturn;
    /** 智能预估净值 */
    private BigDecimal smartEstimateNav;
    /** 策略类型: 统一为 "adaptive" */
    private String smartStrategyType;
    /** 策略描述文案 */
    private String smartDescription;
    /** 场景名 */
    private String smartScenario;
    /** 各源归一化权重 */
    private Map<String, BigDecimal> smartWeights;
    /** 是否应用了准确度修正 */
    private Boolean smartAccuracyEnhanced;
}

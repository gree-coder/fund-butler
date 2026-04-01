package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class EstimateSourceDTO {

    private List<EstimateItem> sources;

    @Data
    public static class EstimateItem {
        private String key;
        private String label;
        private BigDecimal estimateNav;
        private BigDecimal estimateReturn;
        private boolean available;
        private String description;
        /** 策略类型: 统一为 "adaptive"(自适应加权), 仅 smart 源有值 */
        private String strategyType;
        /** 场景名(如"ETF实时"、"权益高覆盖") */
        private String scenario;
        /** 各数据源的归一化权重 */
        private Map<String, BigDecimal> weights;
        /** 是否应用了历史准确度修正 */
        private boolean accuracyEnhanced;
        /** 是否为延迟数据(如QDII T+1) */
        private boolean delayed;
        /** 延迟数据对应的净值日期 */
        private LocalDate delayedDate;
    }
}

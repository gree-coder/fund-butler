package com.qoder.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qoder.fund.entity.EstimatePrediction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface EstimatePredictionMapper extends BaseMapper<EstimatePrediction> {

    /**
     * 获取各数据源的准确度统计
     */
    @Select("SELECT " +
            "  source_key as sourceKey, " +
            "  AVG(ABS(return_error)) as mae, " +
            "  COUNT(*) as predictionCount, " +
            "  SUM(CASE WHEN ABS(return_error) < 0.5 THEN 1 ELSE 0 END) * 1.0 / COUNT(*) as hitRate " +
            "FROM estimate_prediction " +
            "WHERE fund_code = #{fundCode} " +
            "  AND predict_date >= #{startDate} " +
            "  AND actual_return IS NOT NULL " +
            "GROUP BY source_key")
    List<Map<String, Object>> getAccuracyStats(@Param("fundCode") String fundCode,
                                                @Param("startDate") LocalDate startDate);

    /**
     * 获取最近N天的预测记录(用于趋势分析)
     */
    @Select("SELECT * FROM estimate_prediction " +
            "WHERE fund_code = #{fundCode} " +
            "  AND source_key = #{sourceKey} " +
            "  AND actual_return IS NOT NULL " +
            "ORDER BY predict_date DESC " +
            "LIMIT #{limit}")
    List<EstimatePrediction> getRecentPredictions(@Param("fundCode") String fundCode,
                                                   @Param("sourceKey") String sourceKey,
                                                   @Param("limit") int limit);

    /**
     * 获取某段时间内的平均误差(用于趋势判断)
     */
    @Select("SELECT AVG(ABS(return_error)) FROM estimate_prediction " +
            "WHERE fund_code = #{fundCode} " +
            "  AND source_key = #{sourceKey} " +
            "  AND predict_date BETWEEN #{startDate} AND #{endDate} " +
            "  AND actual_return IS NOT NULL")
    BigDecimal getMaeInPeriod(@Param("fundCode") String fundCode,
                               @Param("sourceKey") String sourceKey,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate);
}

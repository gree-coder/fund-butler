package com.qoder.fund.datasource;

import com.qoder.fund.dto.EstimateSourceDTO;
import com.qoder.fund.dto.FundDetailDTO;
import com.qoder.fund.dto.FundSearchDTO;
import com.qoder.fund.dto.RefreshResultDTO;
import com.qoder.fund.entity.EstimatePrediction;
import com.qoder.fund.entity.Fund;
import com.qoder.fund.entity.FundNav;
import com.qoder.fund.mapper.EstimatePredictionMapper;
import com.qoder.fund.mapper.FundMapper;
import com.qoder.fund.mapper.FundNavMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * 多数据源聚合器
 * 主数据源查询 → 失败时降级到备用源 → 估值兜底
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundDataAggregator {

    private final EastMoneyDataSource eastMoneyDataSource;
    private final StockEstimateDataSource stockEstimateDataSource;
    private final SinaDataSource sinaDataSource;
    private final TencentDataSource tencentDataSource;
    private final FundMapper fundMapper;
    private final FundNavMapper fundNavMapper;
    private final EstimatePredictionMapper estimatePredictionMapper;
    private final CacheManager cacheManager;

    /**
     * 搜索基金(带缓存)
     */
    @Cacheable(value = "fundSearch", key = "#keyword", unless = "#result == null || #result.isEmpty()")
    public List<FundSearchDTO> searchFund(String keyword) {
        return eastMoneyDataSource.searchFund(keyword);
    }

    /**
     * 获取基金详情(带缓存)
     */
    @Cacheable(value = "fundDetail", key = "#fundCode", unless = "#result == null")
    public FundDetailDTO getFundDetail(String fundCode) {
        FundDetailDTO detail = eastMoneyDataSource.getFundDetail(fundCode);
        if (detail == null) {
            log.warn("主数据源获取详情失败: {}", fundCode);
            return null;
        }

        // 如果估值为空，尝试股票兜底
        if (detail.getEstimateReturn() == null || detail.getEstimateReturn().compareTo(BigDecimal.ZERO) == 0) {
            tryStockEstimate(fundCode, detail);
        }

        // 持久化基金基本信息
        saveFundInfo(detail);

        return detail;
    }

    /**
     * 获取净值历史
     */
    @Cacheable(value = "navHistory", key = "#fundCode + '_' + #startDate + '_' + #endDate")
    public List<Map<String, Object>> getNavHistory(String fundCode, String startDate, String endDate) {
        return eastMoneyDataSource.getNavHistory(fundCode, startDate, endDate);
    }

    /**
     * 获取实时估值(多源验证+兜底)
     */
    @Cacheable(value = "estimateNav", key = "#fundCode", unless = "#result == null || #result.isEmpty()")
    public Map<String, Object> getEstimateNav(String fundCode) {
        // 主数据源估值
        Map<String, Object> estimate = eastMoneyDataSource.getEstimateNav(fundCode);

        if (estimate != null && !estimate.isEmpty()) {
            return estimate;
        }

        // 兜底: 股票估值
        log.info("使用股票兜底估值: {}", fundCode);
        Fund fund = fundMapper.selectById(fundCode);
        if (fund != null) {
            BigDecimal lastNav = getLatestNav(fundCode);
            if (lastNav != null && lastNav.compareTo(BigDecimal.ZERO) > 0) {
                return stockEstimateDataSource.estimateByStocks(fundCode, lastNav);
            }
        }

        return Collections.emptyMap();
    }

    /**
     * 获取基金最新净值
     */
    public BigDecimal getLatestNav(String fundCode) {
        // 1. 先从数据库查
        try {
            List<FundNav> navs = fundNavMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FundNav>()
                            .eq("fund_code", fundCode)
                            .orderByDesc("nav_date")
                            .last("LIMIT 1")
            );
            if (!navs.isEmpty()) {
                return navs.get(0).getNav();
            }
        } catch (Exception e) {
            log.warn("从数据库获取最新净值失败: {}", fundCode, e);
        }

        // 2. 数据库无数据时，从API获取最新一条净值
        try {
            List<Map<String, Object>> navList = eastMoneyDataSource.getNavHistory(fundCode, "", "");
            if (!navList.isEmpty()) {
                Map<String, Object> latest = navList.get(navList.size() - 1);
                BigDecimal nav = (BigDecimal) latest.get("nav");
                String navDate = (String) latest.get("navDate");
                if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0 && navDate != null) {
                    try {
                        FundNav fundNav = new FundNav();
                        fundNav.setFundCode(fundCode);
                        fundNav.setNavDate(java.time.LocalDate.parse(navDate));
                        fundNav.setNav(nav);
                        fundNav.setAccNav((BigDecimal) latest.get("accNav"));
                        fundNav.setDailyReturn((BigDecimal) latest.get("dailyReturn"));
                        fundNavMapper.insert(fundNav);
                    } catch (Exception ignored) {
                    }
                }
                return nav;
            }
        } catch (Exception e) {
            log.warn("从API获取最新净值失败: {}", fundCode, e);
        }

        return null;
    }

    /**
     * 手动刷新基金数据 (清除缓存后重新获取)
     */
    public RefreshResultDTO refreshFundData(String fundCode) {
        evictCache("fundDetail", fundCode);
        evictCache("estimateNav", fundCode);

        FundDetailDTO detail = getFundDetail(fundCode);
        EstimateSourceDTO estimates = getMultiSourceEstimates(fundCode);

        RefreshResultDTO result = new RefreshResultDTO();
        result.setDetail(detail);
        result.setEstimates(estimates);
        return result;
    }

    /**
     * 获取多数据源估值（供前端切换数据源使用）
     */
    public EstimateSourceDTO getMultiSourceEstimates(String fundCode) {
        EstimateSourceDTO result = new EstimateSourceDTO();
        List<EstimateSourceDTO.EstimateItem> sources = new ArrayList<>();

        BigDecimal lastNav = getLatestNav(fundCode);

        // 数据源0: 检查今日实际净值是否已发布
        EstimateSourceDTO.EstimateItem actualItem = buildActualSource(fundCode);
        if (actualItem != null) {
            sources.add(actualItem);
        }

        // 数据源1: 天天基金实时估值
        EstimateSourceDTO.EstimateItem eastMoneyItem = new EstimateSourceDTO.EstimateItem();
        eastMoneyItem.setKey("eastmoney");
        eastMoneyItem.setLabel("天天基金实时估值");
        eastMoneyItem.setDescription("数据来自天天基金官方估值接口");
        try {
            Map<String, Object> emEstimate = eastMoneyDataSource.getEstimateNav(fundCode);
            if (emEstimate != null && !emEstimate.isEmpty() && emEstimate.get("estimateNav") != null) {
                eastMoneyItem.setEstimateNav((BigDecimal) emEstimate.get("estimateNav"));
                eastMoneyItem.setEstimateReturn((BigDecimal) emEstimate.get("estimateReturn"));
                eastMoneyItem.setAvailable(true);
            } else {
                eastMoneyItem.setAvailable(false);
            }
        } catch (Exception e) {
            log.warn("天天基金估值获取失败: {}", fundCode, e);
            eastMoneyItem.setAvailable(false);
        }
        sources.add(eastMoneyItem);

        // 数据源2: 新浪财经实时估值
        EstimateSourceDTO.EstimateItem sinaItem = new EstimateSourceDTO.EstimateItem();
        sinaItem.setKey("sina");
        sinaItem.setLabel("新浪财经估值");
        sinaItem.setDescription("数据来自新浪财经基金估值接口");
        try {
            Map<String, Object> sinaEstimate = sinaDataSource.getEstimateNav(fundCode);
            if (sinaEstimate != null && !sinaEstimate.isEmpty() && sinaEstimate.get("estimateNav") != null) {
                sinaItem.setEstimateNav((BigDecimal) sinaEstimate.get("estimateNav"));
                sinaItem.setEstimateReturn((BigDecimal) sinaEstimate.get("estimateReturn"));
                sinaItem.setAvailable(true);
            } else {
                sinaItem.setAvailable(false);
            }
        } catch (Exception e) {
            log.warn("新浪财经估值获取失败: {}", fundCode, e);
            sinaItem.setAvailable(false);
        }
        sources.add(sinaItem);

        // 数据源3: 腾讯财经实时估值
        EstimateSourceDTO.EstimateItem tencentItem = new EstimateSourceDTO.EstimateItem();
        tencentItem.setKey("tencent");
        tencentItem.setLabel("腾讯财经估值");
        tencentItem.setDescription("数据来自腾讯财经基金估值接口");
        try {
            Map<String, Object> tencentEstimate = tencentDataSource.getEstimateNav(fundCode);
            if (tencentEstimate != null && !tencentEstimate.isEmpty() && tencentEstimate.get("estimateNav") != null) {
                tencentItem.setEstimateNav((BigDecimal) tencentEstimate.get("estimateNav"));
                tencentItem.setEstimateReturn((BigDecimal) tencentEstimate.get("estimateReturn"));
                tencentItem.setAvailable(true);
            } else {
                tencentItem.setAvailable(false);
            }
        } catch (Exception e) {
            log.warn("腾讯财经估值获取失败: {}", fundCode, e);
            tencentItem.setAvailable(false);
        }
        sources.add(tencentItem);

        // 数据源4: 基于重仓股实时行情加权估算 / ETF实时价格
        EstimateSourceDTO.EstimateItem stockItem = new EstimateSourceDTO.EstimateItem();
        stockItem.setKey("stock");
        stockItem.setLabel("重仓股加权估算");
        stockItem.setDescription("基于基金重仓股的实时行情加权计算");
        String stockSourceType = null;
        BigDecimal coverageRatio = null;
        try {
            if (lastNav != null && lastNav.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> stockEstimate = stockEstimateDataSource.estimateByStocks(fundCode, lastNav);
                if (stockEstimate != null && !stockEstimate.isEmpty() && stockEstimate.get("estimateNav") != null) {
                    stockItem.setEstimateNav((BigDecimal) stockEstimate.get("estimateNav"));
                    stockItem.setEstimateReturn((BigDecimal) stockEstimate.get("estimateReturn"));
                    stockItem.setAvailable(true);
                    stockSourceType = (String) stockEstimate.get("source");
                    coverageRatio = (BigDecimal) stockEstimate.get("coverageRatio");
                    // ETF基金使用实时价格时更新标签
                    if ("etf_realtime".equals(stockSourceType)) {
                        stockItem.setLabel("ETF实时价格");
                        stockItem.setDescription("基于ETF二级市场实时交易价格");
                    }
                } else {
                    stockItem.setAvailable(false);
                }
            } else {
                stockItem.setAvailable(false);
            }
        } catch (Exception e) {
            log.warn("重仓股估值获取失败: {}", fundCode, e);
            stockItem.setAvailable(false);
        }
        sources.add(stockItem);

        // 获取基金类型用于智能预估
        String fundType = null;
        try {
            Fund fund = fundMapper.selectById(fundCode);
            if (fund != null) {
                fundType = fund.getType();
            }
        } catch (Exception ignored) {}

        // 数据源5: 智能综合预估 (基于准确度选源，不受实际净值影响)
        EstimateSourceDTO.EstimateItem smartItem = new EstimateSourceDTO.EstimateItem();
        smartItem.setKey("smart");
        smartItem.setLabel("智能综合预估");

        // 始终基于估值数据源的准确度进行选择，实际净值已单独展示
        buildSmartEstimate(fundCode, eastMoneyItem, sinaItem, tencentItem, stockItem, smartItem,
                fundType, stockSourceType, coverageRatio);
        sources.add(smartItem);

        result.setSources(sources);
        return result;
    }

    private void evictCache(String cacheName, String key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (Exception e) {
            log.warn("清除缓存失败: cache={}, key={}", cacheName, key, e);
        }
    }

    private void tryStockEstimate(String fundCode, FundDetailDTO detail) {
        try {
            BigDecimal lastNav = detail.getLatestNav();
            if (lastNav == null || lastNav.compareTo(BigDecimal.ZERO) == 0) {
                lastNav = getLatestNav(fundCode);
            }
            if (lastNav != null && lastNav.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> stockEstimate = stockEstimateDataSource.estimateByStocks(fundCode, lastNav);
                if (!stockEstimate.isEmpty()) {
                    detail.setEstimateNav((BigDecimal) stockEstimate.get("estimateNav"));
                    detail.setEstimateReturn((BigDecimal) stockEstimate.get("estimateReturn"));
                    detail.setEstimateSource("基于重仓股实时行情加权估算");
                }
            }
        } catch (Exception e) {
            log.warn("股票兜底估值失败: {}", fundCode, e);
        }
    }

    /**
     * 将外部获取的基金信息持久化到数据库
     */
    private void saveFundInfo(FundDetailDTO detail) {
        try {
            Fund fund = fundMapper.selectById(detail.getCode());
            if (fund == null) {
                fund = new Fund();
                fund.setCode(detail.getCode());
            }
            fund.setName(detail.getName());
            fund.setType(detail.getType());
            fund.setCompany(detail.getCompany());
            fund.setManager(detail.getManager());
            fund.setScale(detail.getScale());
            fund.setRiskLevel(detail.getRiskLevel());
            fund.setFeeRate(detail.getFeeRate());
            fund.setTopHoldings(detail.getTopHoldings());
            fund.setIndustryDist(detail.getIndustryDist());

            // 获取完整持仓（年报/半年报）
            try {
                List<Map<String, Object>> allHoldings = eastMoneyDataSource.fetchAllHoldings(detail.getCode());
                if (allHoldings != null && !allHoldings.isEmpty()) {
                    fund.setAllHoldings(allHoldings);
                    detail.setAllHoldings(allHoldings);
                }
            } catch (Exception e) {
                log.warn("获取完整持仓失败: {}", detail.getCode(), e);
            }

            if (detail.getEstablishDate() != null) {
                try {
                    fund.setEstablishDate(java.time.LocalDate.parse(detail.getEstablishDate()));
                } catch (Exception ignored) {}
            }

            if (fundMapper.selectById(detail.getCode()) == null) {
                fundMapper.insert(fund);
            } else {
                fundMapper.updateById(fund);
            }
        } catch (Exception e) {
            log.warn("保存基金信息失败: {}", detail.getCode(), e);
        }
    }

    /**
     * 构建"实际净值"数据源：检查 fund_nav 表中今日是否已有实际净值
     */
    private EstimateSourceDTO.EstimateItem buildActualSource(String fundCode) {
        try {
            LocalDate today = LocalDate.now();
            DayOfWeek dow = today.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                return null;
            }

            List<FundNav> navs = fundNavMapper.selectList(
                    new QueryWrapper<FundNav>()
                            .eq("fund_code", fundCode)
                            .eq("nav_date", today)
                            .last("LIMIT 1")
            );
            if (navs.isEmpty()) {
                // Fallback: 尝试从API获取今日净值
                try {
                    String todayStr = today.toString();
                    List<Map<String, Object>> navList = eastMoneyDataSource.getNavHistory(fundCode, todayStr, todayStr);
                    if (!navList.isEmpty()) {
                        Map<String, Object> navData = navList.get(0);
                        String navDate = (String) navData.get("navDate");
                        if (todayStr.equals(navDate)) {
                            BigDecimal nav = (BigDecimal) navData.get("nav");
                            BigDecimal dailyReturn = (BigDecimal) navData.get("dailyReturn");
                            // 存入DB供后续查询
                            try {
                                FundNav fundNav = new FundNav();
                                fundNav.setFundCode(fundCode);
                                fundNav.setNavDate(today);
                                fundNav.setNav(nav);
                                fundNav.setAccNav((BigDecimal) navData.get("accNav"));
                                fundNav.setDailyReturn(dailyReturn);
                                fundNavMapper.insert(fundNav);
                            } catch (Exception ignored) {}
                            // 构建actual数据源
                            EstimateSourceDTO.EstimateItem item = new EstimateSourceDTO.EstimateItem();
                            item.setKey("actual");
                            item.setLabel("今日实际净值");
                            item.setDescription("今日净值已发布 (来自基金公司官方数据)");
                            item.setEstimateNav(nav);
                            item.setEstimateReturn(dailyReturn != null ? dailyReturn : BigDecimal.ZERO);
                            item.setAvailable(true);
                            return item;
                        }
                    }
                } catch (Exception e) {
                    log.warn("从API获取今日实际净值失败: {}", fundCode, e);
                }
                return null;
            }

            FundNav todayNav = navs.get(0);
            EstimateSourceDTO.EstimateItem item = new EstimateSourceDTO.EstimateItem();
            item.setKey("actual");
            item.setLabel("今日实际净值");
            item.setDescription("今日净值已发布 (来自基金公司官方数据)");
            item.setEstimateNav(todayNav.getNav());
            item.setEstimateReturn(todayNav.getDailyReturn() != null ? todayNav.getDailyReturn() : BigDecimal.ZERO);
            item.setAvailable(true);
            return item;
        } catch (Exception e) {
            log.warn("查询今日实际净值失败: {}", fundCode, e);
            return null;
        }
    }

    /**
     * 获取今日实际净值数据源（供外部调用）
     */
    public EstimateSourceDTO.EstimateItem getActualSource(String fundCode) {
        return buildActualSource(fundCode);
    }

    /**
     * 构建智能综合预估
     * 始终使用多源加权平均，基金类型决定基础权重，历史准确度数据(MAE)动态修正权重
     */
    private void buildSmartEstimate(String fundCode,
                                    EstimateSourceDTO.EstimateItem eastMoneyItem,
                                    EstimateSourceDTO.EstimateItem sinaItem,
                                    EstimateSourceDTO.EstimateItem tencentItem,
                                    EstimateSourceDTO.EstimateItem stockItem,
                                    EstimateSourceDTO.EstimateItem smartItem,
                                    String fundType,
                                    String stockSourceType,
                                    BigDecimal coverageRatio) {
        Map<String, EstimateSourceDTO.EstimateItem> availableSources = new LinkedHashMap<>();
        if (eastMoneyItem.isAvailable()) availableSources.put("eastmoney", eastMoneyItem);
        if (sinaItem.isAvailable()) availableSources.put("sina", sinaItem);
        if (tencentItem.isAvailable()) availableSources.put("tencent", tencentItem);
        if (stockItem.isAvailable()) availableSources.put("stock", stockItem);

        if (availableSources.isEmpty()) {
            smartItem.setAvailable(false);
            smartItem.setDescription("无可用数据源");
            return;
        }

        // Step 1: 基于基金类型确定基础权重
        AdaptiveWeightResult weightResult = determineAdaptiveWeights(fundCode, fundType, stockSourceType, coverageRatio);
        Map<String, BigDecimal> baseWeights = weightResult.weights;

        // Step 2: 尝试用历史准确度数据修正基础权重
        Map<String, BigDecimal> finalWeights;
        boolean accuracyEnhanced = false;
        try {
            Map<String, BigDecimal> multipliers = calculateAccuracyMultipliers(fundCode, availableSources.keySet());
            if (multipliers != null && multipliers.size() >= 2) {
                finalWeights = new LinkedHashMap<>();
                for (String key : availableSources.keySet()) {
                    BigDecimal bw = baseWeights.getOrDefault(key, new BigDecimal("0.25"));
                    BigDecimal multiplier = multipliers.getOrDefault(key, BigDecimal.ONE);
                    finalWeights.put(key, bw.multiply(multiplier));
                }
                accuracyEnhanced = true;
                log.info("基金{}应用准确度修正, 修正因子: {}", fundCode, multipliers);
            } else {
                finalWeights = baseWeights;
            }
        } catch (Exception e) {
            log.warn("准确度修正计算失败，使用基础权重: {}", fundCode, e);
            finalWeights = baseWeights;
        }

        // Step 3: 加权计算
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedNav = BigDecimal.ZERO;
        BigDecimal weightedReturn = BigDecimal.ZERO;

        for (Map.Entry<String, EstimateSourceDTO.EstimateItem> entry : availableSources.entrySet()) {
            BigDecimal w = finalWeights.getOrDefault(entry.getKey(), new BigDecimal("0.25"));
            totalWeight = totalWeight.add(w);
            weightedNav = weightedNav.add(entry.getValue().getEstimateNav().multiply(w));
            weightedReturn = weightedReturn.add(entry.getValue().getEstimateReturn().multiply(w));
        }

        smartItem.setEstimateNav(weightedNav.divide(totalWeight, 4, RoundingMode.HALF_UP));
        smartItem.setEstimateReturn(weightedReturn.divide(totalWeight, 2, RoundingMode.HALF_UP));
        smartItem.setAvailable(true);
        smartItem.setDescription(buildAdaptiveDescription(fundType, stockSourceType, coverageRatio));
        smartItem.setStrategyType("adaptive");
        smartItem.setScenario(weightResult.scenario);
        smartItem.setAccuracyEnhanced(accuracyEnhanced);

        // 计算归一化权重（仅包含实际参与计算的源）
        Map<String, BigDecimal> normalizedWeights = new LinkedHashMap<>();
        for (Map.Entry<String, EstimateSourceDTO.EstimateItem> entry : availableSources.entrySet()) {
            BigDecimal w = finalWeights.getOrDefault(entry.getKey(), new BigDecimal("0.25"));
            normalizedWeights.put(entry.getKey(), w.divide(totalWeight, 4, RoundingMode.HALF_UP));
        }
        smartItem.setWeights(normalizedWeights);
    }

    /**
     * 根据基金类型、stock源类型、覆盖率确定自适应权重
     */
    private AdaptiveWeightResult determineAdaptiveWeights(String fundCode, String fundType,
                                                           String stockSourceType, BigDecimal coverageRatio) {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        String scenario;

        // 场景A: ETF实时价格 — 极高精度
        if ("etf_realtime".equals(stockSourceType)) {
            weights.put("eastmoney", new BigDecimal("0.15"));
            weights.put("sina", new BigDecimal("0.08"));
            weights.put("tencent", new BigDecimal("0.07"));
            weights.put("stock", new BigDecimal("0.70"));
            scenario = "ETF实时";
        }
        // 场景B: 债券/货币基金 — 股票仓位极低
        else if ("BOND".equals(fundType) || "MONEY".equals(fundType)) {
            weights.put("eastmoney", new BigDecimal("0.45"));
            weights.put("sina", new BigDecimal("0.25"));
            weights.put("tencent", new BigDecimal("0.25"));
            weights.put("stock", new BigDecimal("0.05"));
            scenario = "固收类";
        }
        // 场景C: QDII — 海外持仓获取不完整
        else if ("QDII".equals(fundType)) {
            weights.put("eastmoney", new BigDecimal("0.40"));
            weights.put("sina", new BigDecimal("0.25"));
            weights.put("tencent", new BigDecimal("0.25"));
            weights.put("stock", new BigDecimal("0.10"));
            scenario = "QDII";
        }
        // 权益类: 根据覆盖率分档
        else {
            BigDecimal cov = coverageRatio != null ? coverageRatio : BigDecimal.ZERO;
            if (cov.compareTo(new BigDecimal("60")) >= 0) {
                // 场景D: 高覆盖率 ≥60%
                weights.put("eastmoney", new BigDecimal("0.30"));
                weights.put("sina", new BigDecimal("0.18"));
                weights.put("tencent", new BigDecimal("0.17"));
                weights.put("stock", new BigDecimal("0.35"));
                scenario = "权益高覆盖";
            } else if (cov.compareTo(new BigDecimal("30")) >= 0) {
                // 场景E: 中覆盖率 30%-60%
                weights.put("eastmoney", new BigDecimal("0.38"));
                weights.put("sina", new BigDecimal("0.24"));
                weights.put("tencent", new BigDecimal("0.23"));
                weights.put("stock", new BigDecimal("0.15"));
                scenario = "权益中覆盖";
            } else {
                // 场景F: 低覆盖率 <30% (含ETF联接基金)
                weights.put("eastmoney", new BigDecimal("0.43"));
                weights.put("sina", new BigDecimal("0.26"));
                weights.put("tencent", new BigDecimal("0.26"));
                weights.put("stock", new BigDecimal("0.05"));
                scenario = "权益低覆盖";
            }
        }

        log.info("基金{}自适应权重: type={}, stockSource={}, coverage={}%, 场景={}",
                fundCode, fundType, stockSourceType,
                coverageRatio != null ? coverageRatio.setScale(1, RoundingMode.HALF_UP) : "N/A",
                scenario);
        return new AdaptiveWeightResult(weights, scenario);
    }

    /**
     * 生成自适应权重的描述文案
     */
    private String buildAdaptiveDescription(String fundType, String stockSourceType, BigDecimal coverageRatio) {
        if ("etf_realtime".equals(stockSourceType)) {
            return "基于ETF实时价格的高置信度加权";
        }
        if ("BOND".equals(fundType) || "MONEY".equals(fundType)) {
            return "固收类基金加权平均（以机构估值为主）";
        }
        if ("QDII".equals(fundType)) {
            return "QDII基金加权平均（海外持仓估算可靠性低）";
        }
        BigDecimal cov = coverageRatio != null ? coverageRatio : BigDecimal.ZERO;
        String covStr = cov.setScale(0, RoundingMode.HALF_UP).toPlainString();
        if (cov.compareTo(new BigDecimal("60")) >= 0) {
            return "多源加权平均（重仓股覆盖率" + covStr + "%，权重35%）";
        } else if (cov.compareTo(new BigDecimal("30")) >= 0) {
            return "多源加权平均（重仓股覆盖率" + covStr + "%，权重降至15%）";
        } else {
            return "多源加权平均（重仓股覆盖率仅" + covStr + "%，权重降至5%）";
        }
    }

    /** 自适应权重计算结果 */
    private static class AdaptiveWeightResult {
        final Map<String, BigDecimal> weights;
        final String scenario;

        AdaptiveWeightResult(Map<String, BigDecimal> weights, String scenario) {
            this.weights = weights;
            this.scenario = scenario;
        }
    }

    /**
     * 基于历史预测准确度计算各数据源的权重修正乘数
     * 查询 estimate_prediction 表最近3条有实际值的记录，计算各源MAE
     * MAE越低的源乘数越高，MAE越高的源乘数越低
     * 公式: multiplier = 1 / (1 + MAE)，无数据的源乘数为1（不修正）
     * 返回null表示数据不足，不应用修正
     */
    private Map<String, BigDecimal> calculateAccuracyMultipliers(String fundCode, Set<String> sourceKeys) {
        List<EstimatePrediction> predictions = estimatePredictionMapper.selectList(
                new QueryWrapper<EstimatePrediction>()
                        .eq("fund_code", fundCode)
                        .isNotNull("actual_return")
                        .in("source_key", sourceKeys)
                        .orderByDesc("predict_date")
        );

        if (predictions.isEmpty()) {
            return null;
        }

        // 按 source_key 分组
        Map<String, List<EstimatePrediction>> bySource = new LinkedHashMap<>();
        for (EstimatePrediction p : predictions) {
            bySource.computeIfAbsent(p.getSourceKey(), k -> new ArrayList<>()).add(p);
        }

        // 计算各源MAE（至少3条记录才参与）
        Map<String, BigDecimal> maeMap = new LinkedHashMap<>();
        int minRecords = 3;

        for (Map.Entry<String, List<EstimatePrediction>> entry : bySource.entrySet()) {
            List<EstimatePrediction> records = entry.getValue();
            List<EstimatePrediction> recent = records.size() > minRecords ? records.subList(0, minRecords) : records;
            if (recent.size() < minRecords) {
                continue;
            }

            BigDecimal sumError = BigDecimal.ZERO;
            for (EstimatePrediction p : recent) {
                BigDecimal error = p.getReturnError() != null ? p.getReturnError().abs() : BigDecimal.ZERO;
                sumError = sumError.add(error);
            }
            BigDecimal mae = sumError.divide(new BigDecimal(recent.size()), 4, RoundingMode.HALF_UP);
            maeMap.put(entry.getKey(), mae);
        }

        if (maeMap.size() < 2) {
            return null;
        }

        // 转换为修正乘数: multiplier = 1 / (1 + MAE)
        // MAE=0 → multiplier=1.0(最高), MAE=1 → multiplier=0.5, MAE=2 → multiplier=0.33
        Map<String, BigDecimal> multipliers = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : maeMap.entrySet()) {
            BigDecimal multiplier = BigDecimal.ONE.divide(
                    BigDecimal.ONE.add(entry.getValue()), 4, RoundingMode.HALF_UP);
            multipliers.put(entry.getKey(), multiplier);
        }
        // 无历史数据的源乘数为1（不修正）

        log.info("基金{}准确度修正乘数: {}", fundCode, multipliers);
        return multipliers;
    }
}

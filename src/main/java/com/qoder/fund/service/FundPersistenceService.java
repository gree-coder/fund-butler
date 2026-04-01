package com.qoder.fund.service;

import com.qoder.fund.dto.FundDetailDTO;
import com.qoder.fund.entity.Fund;
import com.qoder.fund.entity.FundNav;
import com.qoder.fund.mapper.FundMapper;
import com.qoder.fund.mapper.FundNavMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 基金数据持久化服务
 * 负责基金基本信息和净值数据的持久化操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundPersistenceService {

    private final FundMapper fundMapper;
    private final FundNavMapper fundNavMapper;

    /**
     * 保存或更新基金基本信息
     */
    @Transactional
    public void saveFundInfo(FundDetailDTO detail) {
        if (detail == null || detail.getCode() == null) {
            return;
        }

        try {
            Fund existing = fundMapper.selectById(detail.getCode());
            Fund fund = new Fund();
            fund.setCode(detail.getCode());
            fund.setName(detail.getName());
            fund.setType(detail.getType());
            fund.setCompany(detail.getCompany());
            fund.setManager(detail.getManager());
            if (detail.getEstablishDate() != null) {
                try {
                    fund.setEstablishDate(LocalDate.parse(detail.getEstablishDate()));
                } catch (Exception e) {
                    log.warn("解析成立日期失败: {}", detail.getEstablishDate());
                }
            }
            fund.setScale(detail.getScale());
            fund.setRiskLevel(detail.getRiskLevel());
            fund.setFeeRate(detail.getFeeRate());
            fund.setTopHoldings(detail.getTopHoldings());
            fund.setAllHoldings(detail.getAllHoldings());
            fund.setIndustryDist(detail.getIndustryDist());
            fund.setUpdatedAt(LocalDateTime.now());

            if (existing == null) {
                fundMapper.insert(fund);
                log.info("新增基金: {}", detail.getCode());
            } else {
                fundMapper.updateById(fund);
            }
        } catch (Exception e) {
            log.warn("保存基金信息失败: {}", detail.getCode(), e);
        }
    }

    /**
     * 批量保存净值历史
     */
    @Transactional
    public int saveNavHistory(String fundCode, List<Map<String, Object>> navList) {
        if (navList == null || navList.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Map<String, Object> item : navList) {
            try {
                LocalDate navDate = LocalDate.parse((String) item.get("navDate"));

                // 检查是否已存在
                Long existing = fundNavMapper.selectCount(
                        new QueryWrapper<FundNav>()
                                .eq("fund_code", fundCode)
                                .eq("nav_date", navDate)
                );

                if (existing > 0) {
                    continue; // 已存在，跳过
                }

                FundNav nav = new FundNav();
                nav.setFundCode(fundCode);
                nav.setNavDate(navDate);
                nav.setNav((BigDecimal) item.get("nav"));
                nav.setAccNav((BigDecimal) item.get("accNav"));
                nav.setDailyReturn((BigDecimal) item.get("dailyReturn"));
                fundNavMapper.insert(nav);
                count++;
            } catch (Exception e) {
                log.warn("保存净值失败: {}", fundCode, e);
            }
        }

        if (count > 0) {
            log.info("保存 {} 条净值记录: {}", count, fundCode);
        }
        return count;
    }

    /**
     * 获取基金类型
     */
    public String getFundType(String fundCode) {
        try {
            Fund fund = fundMapper.selectById(fundCode);
            return fund != null ? fund.getType() : null;
        } catch (Exception e) {
            log.warn("获取基金类型失败: {}", fundCode, e);
            return null;
        }
    }
}

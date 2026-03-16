package com.qoder.fund.scheduler;

import com.qoder.fund.datasource.EastMoneyDataSource;
import com.qoder.fund.entity.Fund;
import com.qoder.fund.entity.FundNav;
import com.qoder.fund.mapper.FundMapper;
import com.qoder.fund.mapper.FundNavMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class FundDataSyncScheduler {

    private final FundMapper fundMapper;
    private final FundNavMapper fundNavMapper;
    private final EastMoneyDataSource eastMoneyDataSource;

    /**
     * 每交易日19:30同步净值数据
     */
    @Scheduled(cron = "0 30 19 * * MON-FRI")
    public void syncDailyNav() {
        if (!isTradingDay(LocalDate.now())) return;

        log.info("开始同步每日净值数据...");
        List<Fund> funds = fundMapper.selectList(null);
        int count = 0;

        for (Fund fund : funds) {
            try {
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                List<Map<String, Object>> navList = eastMoneyDataSource.getNavHistory(
                        fund.getCode(), today, today);

                for (Map<String, Object> navData : navList) {
                    saveNav(fund.getCode(), navData);
                    count++;
                }
            } catch (Exception e) {
                log.warn("同步净值失败: fund={}", fund.getCode(), e);
            }
        }
        log.info("净值同步完成, 更新 {} 条记录", count);
    }

    /**
     * 判断是否为交易日 (简单判断：排除周末)
     */
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private void saveNav(String fundCode, Map<String, Object> navData) {
        try {
            String navDate = (String) navData.get("navDate");
            if (navDate == null || navDate.isEmpty()) return;

            // 检查是否已存在
            long exists = fundNavMapper.selectCount(
                    new QueryWrapper<FundNav>()
                            .eq("fund_code", fundCode)
                            .eq("nav_date", navDate));
            if (exists > 0) return;

            FundNav nav = new FundNav();
            nav.setFundCode(fundCode);
            nav.setNavDate(LocalDate.parse(navDate));
            nav.setNav(toBigDecimal(navData.get("nav")));
            nav.setAccNav(toBigDecimal(navData.get("accNav")));
            nav.setDailyReturn(toBigDecimal(navData.get("dailyReturn")));
            fundNavMapper.insert(nav);
        } catch (Exception e) {
            log.warn("保存净值失败: fund={}", fundCode, e);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

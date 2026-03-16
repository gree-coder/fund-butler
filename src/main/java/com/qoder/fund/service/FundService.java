package com.qoder.fund.service;

import com.qoder.fund.datasource.FundDataAggregator;
import com.qoder.fund.dto.FundDetailDTO;
import com.qoder.fund.dto.FundSearchDTO;
import com.qoder.fund.dto.NavHistoryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FundService {

    private final FundDataAggregator dataAggregator;

    public List<FundSearchDTO> search(String keyword) {
        if (keyword == null || keyword.trim().length() < 2) {
            return Collections.emptyList();
        }
        return dataAggregator.searchFund(keyword.trim());
    }

    public FundDetailDTO getDetail(String fundCode) {
        return dataAggregator.getFundDetail(fundCode);
    }

    public NavHistoryDTO getNavHistory(String fundCode, String period) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = switch (period) {
            case "1m" -> endDate.minusMonths(1);
            case "3m" -> endDate.minusMonths(3);
            case "6m" -> endDate.minusMonths(6);
            case "1y" -> endDate.minusYears(1);
            case "3y" -> endDate.minusYears(3);
            case "all" -> endDate.minusYears(20);
            default -> endDate.minusMonths(3);
        };

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        List<Map<String, Object>> rawList = dataAggregator.getNavHistory(
                fundCode, startDate.format(fmt), endDate.format(fmt));

        NavHistoryDTO dto = new NavHistoryDTO();
        List<String> dates = new ArrayList<>();
        List<BigDecimal> navs = new ArrayList<>();

        for (Map<String, Object> item : rawList) {
            dates.add((String) item.get("navDate"));
            navs.add((BigDecimal) item.get("nav"));
        }

        dto.setDates(dates);
        dto.setNavs(navs);
        return dto;
    }
}

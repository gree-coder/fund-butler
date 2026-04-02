package com.qoder.fund.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * 交易日历服务
 * 判断是否为中国A股交易日（排除周末和法定节假日）
 */
@Service
public class TradingCalendarService {

    /**
     * 中国A股法定节假日（2024-2026年）
     * 数据来源：上海证券交易所/深圳证券交易所公告
     */
    private static final Set<LocalDate> HOLIDAYS = new HashSet<>();

    static {
        // 2024年节假日
        // 元旦：2024-01-01
        HOLIDAYS.add(LocalDate.of(2024, 1, 1));
        // 春节：2024-02-10 ~ 2024-02-17
        for (int d = 10; d <= 17; d++) {
            HOLIDAYS.add(LocalDate.of(2024, 2, d));
        }
        // 清明：2024-04-04 ~ 2024-04-06
        for (int d = 4; d <= 6; d++) {
            HOLIDAYS.add(LocalDate.of(2024, 4, d));
        }
        // 劳动节：2024-05-01 ~ 2024-05-05
        for (int d = 1; d <= 5; d++) {
            HOLIDAYS.add(LocalDate.of(2024, 5, d));
        }
        // 端午：2024-06-10
        HOLIDAYS.add(LocalDate.of(2024, 6, 10));
        // 中秋：2024-09-15 ~ 2024-09-17
        for (int d = 15; d <= 17; d++) {
            HOLIDAYS.add(LocalDate.of(2024, 9, d));
        }
        // 国庆：2024-10-01 ~ 2024-10-07
        for (int d = 1; d <= 7; d++) {
            HOLIDAYS.add(LocalDate.of(2024, 10, d));
        }

        // 2025年节假日
        // 元旦：2025-01-01
        HOLIDAYS.add(LocalDate.of(2025, 1, 1));
        // 春节：2025-01-28 ~ 2025-02-04
        for (int d = 28; d <= 31; d++) {
            HOLIDAYS.add(LocalDate.of(2025, 1, d));
        }
        for (int d = 1; d <= 4; d++) {
            HOLIDAYS.add(LocalDate.of(2025, 2, d));
        }
        // 清明：2025-04-04 ~ 2025-04-06
        for (int d = 4; d <= 6; d++) {
            HOLIDAYS.add(LocalDate.of(2025, 4, d));
        }
        // 劳动节：2025-05-01 ~ 2025-05-05
        for (int d = 1; d <= 5; d++) {
            HOLIDAYS.add(LocalDate.of(2025, 5, d));
        }
        // 端午：2025-05-31 ~ 2025-06-02
        HOLIDAYS.add(LocalDate.of(2025, 5, 31));
        for (int d = 1; d <= 2; d++) {
            HOLIDAYS.add(LocalDate.of(2025, 6, d));
        }
        // 中秋+国庆：2025-10-01 ~ 2025-10-08
        for (int d = 1; d <= 8; d++) {
            HOLIDAYS.add(LocalDate.of(2025, 10, d));
        }

        // 2026年节假日（预估，以官方公告为准）
        // 元旦：2026-01-01 ~ 2026-01-03
        for (int d = 1; d <= 3; d++) {
            HOLIDAYS.add(LocalDate.of(2026, 1, d));
        }
        // 春节：2026-02-17 ~ 2026-02-23（预估）
        for (int d = 17; d <= 23; d++) {
            HOLIDAYS.add(LocalDate.of(2026, 2, d));
        }
        // 清明：2026-04-04 ~ 2026-04-06（预估）
        for (int d = 4; d <= 6; d++) {
            HOLIDAYS.add(LocalDate.of(2026, 4, d));
        }
        // 劳动节：2026-05-01 ~ 2026-05-05（预估）
        for (int d = 1; d <= 5; d++) {
            HOLIDAYS.add(LocalDate.of(2026, 5, d));
        }
        // 端午：2026-05-20 ~ 2026-05-22（预估）
        for (int d = 20; d <= 22; d++) {
            HOLIDAYS.add(LocalDate.of(2026, 5, d));
        }
        // 中秋：2026-09-12 ~ 2026-09-14（预估）
        for (int d = 12; d <= 14; d++) {
            HOLIDAYS.add(LocalDate.of(2026, 9, d));
        }
        // 国庆：2026-10-01 ~ 2026-10-07
        for (int d = 1; d <= 7; d++) {
            HOLIDAYS.add(LocalDate.of(2026, 10, d));
        }
    }

    /**
     * 周末补班日（股市开市的周末）
     */
    private static final Set<LocalDate> WORKDAYS_ON_WEEKEND = new HashSet<>();

    static {
        // 2024年调休补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2024, 2, 4));   // 春节前周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2024, 2, 18));  // 春节后周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2024, 4, 7));   // 清明后周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2024, 4, 28));  // 五一前周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2024, 5, 11));  // 五一后周六补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2024, 9, 14));  // 中秋前周六补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2024, 9, 29));  // 国庆前周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2024, 10, 12)); // 国庆后周六补班

        // 2025年调休补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2025, 1, 26));  // 春节前周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2025, 2, 8));   // 春节后周六补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2025, 4, 27));  // 五一前周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2025, 9, 28));  // 国庆前周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2025, 10, 11)); // 国庆后周六补班

        // 2026年调休补班（预估）
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2026, 2, 15));  // 春节前周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2026, 2, 24));  // 春节后周二补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2026, 9, 27));  // 国庆前周日补班
        WORKDAYS_ON_WEEKEND.add(LocalDate.of(2026, 10, 10)); // 国庆后周六补班
    }

    /**
     * 判断是否为A股交易日
     *
     * @param date 日期
     * @return 是否为交易日
     */
    public boolean isTradingDay(LocalDate date) {
        // 检查是否为节假日
        if (HOLIDAYS.contains(date)) {
            return false;
        }

        // 检查是否为周末补班日
        if (WORKDAYS_ON_WEEKEND.contains(date)) {
            return true;
        }

        // 检查是否为周末
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    /**
     * 判断是否为美股交易日
     * 美股节假日与A股不同，此处简化处理
     *
     * @param date 日期
     * @return 是否为美股交易日
     */
    public boolean isUsTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        // 美股周末休市
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false;
        }

        // 美股主要节假日（简化版）
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        // 元旦：1月1日
        if (month == 1 && day == 1) return false;

        // 美国独立日：7月4日
        if (month == 7 && day == 4) return false;

        // 圣诞节：12月25日
        if (month == 12 && day == 25) return false;

        // 注：美国感恩节（11月第四个周四）、马丁路德金日等动态节日未在此实现
        // 如需精确判断，建议接入外部日历API

        return true;
    }

    /**
     * 判断当前是否处于美股夏令时
     * 美国夏令时：3月第二个周日 ~ 11月第一个周日
     *
     * @return true=夏令时(美股21:30开盘), false=冬令时(美股22:30开盘)
     */
    public boolean isUsDaylightSaving() {
        LocalDate today = LocalDate.now();
        return isUsDaylightSaving(today);
    }

    /**
     * 判断指定日期是否处于美股夏令时
     */
    public boolean isUsDaylightSaving(LocalDate date) {
        int year = date.getYear();

        // 夏令时开始：3月第二个周日
        LocalDate dstStart = findNthDayOfWeek(year, 3, DayOfWeek.SUNDAY, 2);
        // 夏令时结束：11月第一个周日
        LocalDate dstEnd = findNthDayOfWeek(year, 11, DayOfWeek.SUNDAY, 1);

        return !date.isBefore(dstStart) && date.isBefore(dstEnd);
    }

    /**
     * 找到某月第n个星期几
     */
    private LocalDate findNthDayOfWeek(int year, int month, DayOfWeek dayOfWeek, int n) {
        LocalDate date = LocalDate.of(year, month, 1);
        int count = 0;
        while (count < n) {
            if (date.getDayOfWeek() == dayOfWeek) {
                count++;
                if (count == n) return date;
            }
            date = date.plusDays(1);
        }
        return date;
    }
}

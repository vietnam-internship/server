package com.fptis.intern.server.domain.branch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BusinessHoursParserTest {

    private static final String HOURS = "평일 09:00-18:00, 토 09:00-13:00";

    @Test
    void openOnWeekdayWithinRange() {
        LocalDateTime wednesdayNoon = LocalDateTime.of(2026, 7, 22, 12, 0); // 수요일
        assertThat(BusinessHoursParser.isOpenAt(HOURS, wednesdayNoon)).isTrue();
    }

    @Test
    void closedOnWeekdayOutsideRange() {
        LocalDateTime wednesdayEvening = LocalDateTime.of(2026, 7, 22, 19, 0);
        assertThat(BusinessHoursParser.isOpenAt(HOURS, wednesdayEvening)).isFalse();
    }

    @Test
    void openOnSaturdayWithinSaturdayRange() {
        LocalDateTime saturdayMorning = LocalDateTime.of(2026, 7, 25, 10, 0); // 토요일
        assertThat(BusinessHoursParser.isOpenAt(HOURS, saturdayMorning)).isTrue();
    }

    @Test
    void closedOnSundayWhenNoSegmentCoversIt() {
        LocalDateTime sundayNoon = LocalDateTime.of(2026, 7, 26, 12, 0); // 일요일
        assertThat(BusinessHoursParser.isOpenAt(HOURS, sundayNoon)).isFalse();
    }

    @Test
    void closedWhenFormatIsUnparseable() {
        LocalDateTime wednesdayNoon = LocalDateTime.of(2026, 7, 22, 12, 0);
        assertThat(BusinessHoursParser.isOpenAt("연중무휴 24시간", wednesdayNoon)).isFalse();
    }

    @Test
    void closedWhenBlank() {
        LocalDateTime wednesdayNoon = LocalDateTime.of(2026, 7, 22, 12, 0);
        assertThat(BusinessHoursParser.isOpenAt("", wednesdayNoon)).isFalse();
        assertThat(BusinessHoursParser.isOpenAt(null, wednesdayNoon)).isFalse();
    }
}

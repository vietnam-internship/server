package com.fptis.intern.server.domain.branch;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * businessHours는 "평일 09:00-18:00, 토 09:00-13:00"처럼 자유 텍스트로 저장되므로
 * 이 콤마 구분 "요일/평일/주말 HH:mm-HH:mm" 세그먼트 포맷만 best-effort로 파싱한다.
 * 포맷을 벗어난 세그먼트는 무시하고, 매칭되는 세그먼트가 없으면 영업 중이 아닌 것으로 본다.
 */
final class BusinessHoursParser {

    private static final Pattern SEGMENT =
            Pattern.compile("(평일|주말|월|화|수|목|금|토|일)\\s+(\\d{2}:\\d{2})-(\\d{2}:\\d{2})");

    private BusinessHoursParser() {
    }

    static boolean isOpenAt(String businessHours, LocalDateTime at) {
        if (businessHours == null || businessHours.isBlank()) {
            return false;
        }

        DayOfWeek day = at.getDayOfWeek();
        LocalTime time = at.toLocalTime();

        for (String segment : businessHours.split(",")) {
            Matcher matcher = SEGMENT.matcher(segment.trim());
            if (!matcher.matches() || !matchesDay(matcher.group(1), day)) {
                continue;
            }
            if (isWithin(time, matcher.group(2), matcher.group(3))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWithin(LocalTime time, String startText, String endText) {
        try {
            LocalTime start = LocalTime.parse(startText);
            LocalTime end = LocalTime.parse(endText);
            return !time.isBefore(start) && !time.isAfter(end);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean matchesDay(String token, DayOfWeek day) {
        return switch (token) {
            case "평일" -> day.getValue() <= DayOfWeek.FRIDAY.getValue();
            case "주말" -> day.getValue() >= DayOfWeek.SATURDAY.getValue();
            case "월" -> day == DayOfWeek.MONDAY;
            case "화" -> day == DayOfWeek.TUESDAY;
            case "수" -> day == DayOfWeek.WEDNESDAY;
            case "목" -> day == DayOfWeek.THURSDAY;
            case "금" -> day == DayOfWeek.FRIDAY;
            case "토" -> day == DayOfWeek.SATURDAY;
            case "일" -> day == DayOfWeek.SUNDAY;
            default -> false;
        };
    }
}

package com.fptis.intern.server.domain.branch;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * businessHours는 "평일 09:00-18:00, 토 09:00-13:00" 또는 "Weekday 09:00-18:00, Sat 09:00-13:00"처럼
 * 자유 텍스트로 저장되므로 이 콤마 구분 "요일/평일/주말 HH:mm-HH:mm" 세그먼트 포맷만 best-effort로 파싱한다.
 * 한글/영어 요일 토큰을 모두 지원한다. 포맷을 벗어난 세그먼트는 무시하고, 매칭되는 세그먼트가 없으면
 * 영업 중이 아닌 것으로 본다.
 */
final class BusinessHoursParser {

    private static final Pattern SEGMENT =
            Pattern.compile(
                    "(평일|주말|월|화|수|목|금|토|일|Weekday|Weekend|Mon|Tue|Wed|Thu|Fri|Sat|Sun)"
                            + "\\s+(\\d{2}:\\d{2})-(\\d{2}:\\d{2})",
                    Pattern.CASE_INSENSITIVE);

    private BusinessHoursParser() {
    }

    /**
     * 특정 요일의 open~close 범위를 반환한다. 매칭되는 세그먼트가 없거나 시간 파싱에 실패하면
     * empty(그 날은 휴무로 본다). 시간대 조회(가용 슬롯 계산)에 쓰인다 — isOpenAt과 같은 세그먼트
     * 포맷을 공유하되, 특정 순간이 아니라 그 날 전체 open/close 쌍이 필요할 때 쓴다.
     */
    static Optional<LocalTime[]> rangeFor(String businessHours, DayOfWeek day) {
        if (businessHours == null || businessHours.isBlank()) {
            return Optional.empty();
        }

        for (String segment : businessHours.split(",")) {
            Matcher matcher = SEGMENT.matcher(segment.trim());
            if (!matcher.matches() || !matchesDay(matcher.group(1), day)) {
                continue;
            }
            try {
                LocalTime open = LocalTime.parse(matcher.group(2));
                LocalTime close = LocalTime.parse(matcher.group(3));
                return Optional.of(new LocalTime[]{open, close});
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
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
        return switch (token.toLowerCase()) {
            case "평일", "weekday" -> day.getValue() <= DayOfWeek.FRIDAY.getValue();
            case "주말", "weekend" -> day.getValue() >= DayOfWeek.SATURDAY.getValue();
            case "월", "mon" -> day == DayOfWeek.MONDAY;
            case "화", "tue" -> day == DayOfWeek.TUESDAY;
            case "수", "wed" -> day == DayOfWeek.WEDNESDAY;
            case "목", "thu" -> day == DayOfWeek.THURSDAY;
            case "금", "fri" -> day == DayOfWeek.FRIDAY;
            case "토", "sat" -> day == DayOfWeek.SATURDAY;
            case "일", "sun" -> day == DayOfWeek.SUNDAY;
            default -> false;
        };
    }
}

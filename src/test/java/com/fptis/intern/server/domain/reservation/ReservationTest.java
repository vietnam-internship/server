package com.fptis.intern.server.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ReservationTest {

    private Reservation newReservation(LocalDateTime now) {
        return Reservation.builder()
                .userId(1L)
                .branchId(1L)
                .currencyCode("USD")
                .amount(500)
                .pickupDate(LocalDate.of(2026, 7, 22))
                .pickupTime(LocalTime.of(10, 30))
                .now(now)
                .build();
    }

    @Test
    void holdsStockForTwoHoursFromCreation() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 21, 10, 0);
        Reservation reservation = newReservation(now);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservation.getExpiresAt()).isEqualTo(now.plusHours(2));
        assertThat(reservation.isExpired(now.plusHours(2).minusMinutes(1))).isFalse();
        assertThat(reservation.isExpired(now.plusHours(2).plusMinutes(1))).isTrue();
    }

    @Test
    void cancelClearsQrTokenAndMarksAutoExpiredWhenRequested() {
        Reservation reservation = newReservation(LocalDateTime.now());
        reservation.issueQrToken("raw-token");

        reservation.cancel(true);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.isAutoExpired()).isTrue();
        assertThat(reservation.getQrToken()).isNull();
    }

    @Test
    void cancelTwiceThrowsAlreadyCancelled() {
        Reservation reservation = newReservation(LocalDateTime.now());
        reservation.cancel(false);

        assertThatThrownBy(() -> reservation.cancel(false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.ALREADY_CANCELLED);
    }

    @Test
    void cancelAfterCompleteThrowsAlreadyCompleted() {
        Reservation reservation = newReservation(LocalDateTime.now());
        reservation.complete(LocalDateTime.now());

        assertThatThrownBy(() -> reservation.cancel(false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(BusinessErrorCode.ALREADY_COMPLETED);
    }

    @Test
    void completeClearsQrToken() {
        Reservation reservation = newReservation(LocalDateTime.now());
        reservation.issueQrToken("raw-token");
        LocalDateTime pickedUpAt = LocalDateTime.now();

        reservation.complete(pickedUpAt);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(reservation.getPickedUpAt()).isEqualTo(pickedUpAt);
        assertThat(reservation.getQrToken()).isNull();
    }
}

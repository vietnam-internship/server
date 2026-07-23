package com.fptis.intern.server.domain.currency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ExchangeRateHistoryRepository extends JpaRepository<ExchangeRateHistory, Long> {
    List<ExchangeRateHistory> findByCurrencyIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(Long currencyId, LocalDate startDate);
}

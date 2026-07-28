package com.fptis.intern.server.application.currency;

import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.domain.currency.ExchangeRateHistory;
import com.fptis.intern.server.domain.currency.ExchangeRateHistoryRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.presentation.currency.dto.CurrencyDetail;
import com.fptis.intern.server.presentation.currency.dto.CurrencyListResponse;
import com.fptis.intern.server.presentation.currency.dto.CurrencySummary;
import com.fptis.intern.server.presentation.currency.dto.RateHistoryEntry;
import com.fptis.intern.server.presentation.currency.dto.TimingRecommendation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrencyService {

    private final CurrencyRepository currencyRepository;
    private final ExchangeRateHistoryRepository historyRepository;

    /**
     * 통화 목록 조회 (검색어 q가 있으면 필터링, 없으면 전체 및 인기 통화 반환)
     */
    public CurrencyListResponse getCurrencies(String q) {
        List<CurrencySummary> results;
        if (q != null && !q.trim().isEmpty()) {
            results = currencyRepository.searchByCodeOrCountry(q)
                    .stream()
                    .map(CurrencySummary::from)
                    .collect(Collectors.toList());
            return CurrencyListResponse.of(results, Collections.emptyList(), Collections.emptyList());
        } else {
            results = currencyRepository.findAll()
                    .stream()
                    .map(CurrencySummary::from)
                    .collect(Collectors.toList());
            // MVP 단계: 최근 검색어는 세션이 필요하므로 빈 리스트, 인기 통화는 상위 5개 임의 추출
            List<CurrencySummary> popular = results.stream().limit(5).collect(Collectors.toList());
            return CurrencyListResponse.of(results, Collections.emptyList(), popular);
        }
    }

    /**
     * 통화 단건 상세 조회
     */
    public CurrencyDetail getCurrencyDetail(String code) {
        Currency currency = currencyRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.CURRENCY_NOT_FOUND));

        boolean highVolatility = calculateHighVolatility(currency);
        return CurrencyDetail.of(currency, highVolatility);
    }

    /**
     * 특정 통화의 최근 환율 변동 이력 조회
     */
    public List<RateHistoryEntry> getCurrencyHistory(String code, int days) {
        Currency currency = currencyRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.CURRENCY_NOT_FOUND));

        LocalDate startDate = LocalDate.now().minusDays(days);
        List<ExchangeRateHistory> historyList = historyRepository.findByCurrencyIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(currency.getId(), startDate);

        return historyList.stream()
                .map(RateHistoryEntry::from)
                .collect(Collectors.toList());
    }

    /**
     * 환전 타이밍 AI 추천 정보 조회
     * 7일 이평선(MA7) 기준으로 현재 환율 위치를 판단해 signal을 결정한다.
     * - history < 7일: COLLECTING_DATA
     * - currentRate > MA7 × 1.005: WAIT (평균보다 비쌈)
     * - currentRate < MA7 × 0.995: NOW (평균보다 쌈)
     * - 그 외: NEUTRAL
     */
    public TimingRecommendation getTimingRecommendation(String code) {
        Currency currency = currencyRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.CURRENCY_NOT_FOUND));

        LocalDate startDate = LocalDate.now().minusDays(7);
        List<ExchangeRateHistory> history = historyRepository
                .findByCurrencyIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(currency.getId(), startDate);

        boolean highVolatility = calculateHighVolatility(currency);
        double currentRate = currency.getBuyRate();

        double ma7 = history.stream()
                .mapToDouble(ExchangeRateHistory::getRate)
                .average()
                .orElse(currentRate);

        String signal;
        if (currentRate > ma7 * 1.005) {
            signal = "WAIT";
        } else if (currentRate < ma7 * 0.995) {
            signal = "NOW";
        } else {
            signal = "NEUTRAL";
        }

        return TimingRecommendation.of(currency.getCode(), signal, currentRate, null, highVolatility);
    }

    /**
     * 고변동성 여부 계산 (현재는 임시로 false 반환, 추후 이력 데이터 분석 로직 추가)
     */
    private boolean calculateHighVolatility(Currency currency) {
        return false;
    }
}

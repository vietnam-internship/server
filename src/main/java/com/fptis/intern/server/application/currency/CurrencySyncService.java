package com.fptis.intern.server.application.currency;

import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.domain.currency.ExchangeRateHistory;
import com.fptis.intern.server.domain.currency.ExchangeRateHistoryRepository;
import com.fptis.intern.server.presentation.currency.dto.ExchangeRateApiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencySyncService {

    private final CurrencyRepository currencyRepository;
    private final ExchangeRateHistoryRepository historyRepository;
    private final RestTemplate restTemplate;

    @Value("${EXCHANGERATE_API_KEY}")
    private String apiKey;

    @Value("${EXCHANGERATE_API_URL}")
    private String apiUrl;

    // 매일 자정 실행 (cron = "초 분 시 일 월 요일")
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void syncExchangeRates() {
        log.info("환율 동기화 배치를 시작합니다.");

        try {
            String url = String.format(apiUrl, apiKey);
            ExchangeRateApiResponseDto response = restTemplate.getForObject(url, ExchangeRateApiResponseDto.class);

            if (response == null || !"success".equals(response.result())) {
                log.error("환율 API 호출 실패: {}", response);
                return;
            }

            Map<String, Double> rates = response.conversion_rates();
            if (rates == null || rates.isEmpty()) {
                log.error("환율 데이터가 비어있습니다.");
                return;
            }

            List<Currency> currencies = currencyRepository.findAll();
            LocalDate today = LocalDate.now();

            for (Currency currency : currencies) {
                String code = currency.getCode();
                Double targetRate = rates.get(code);

                if (targetRate != null) {
                    // API 응답은 1 KRW = targetRate 통화 
                    // 따라서 1 통화 = 1 / targetRate KRW
                    double baseRate = 1.0 / targetRate;
                    
                    // 매입/매도 스프레드 설정 (간단히 1.5% 적용)
                    double spread = baseRate * 0.015;
                    double buyRate = baseRate - spread;
                    double sellRate = baseRate + spread;

                    currency.updateRates(buyRate, sellRate);

                    ExchangeRateHistory history = ExchangeRateHistory.builder()
                            .currency(currency)
                            .rate(baseRate)
                            .recordedAt(today)
                            .build();
                    historyRepository.save(history);

                    log.info("환율 갱신 완료 - [{}]: 기준 {} KRW", code, baseRate);
                } else {
                    log.warn("API 응답에 지원하지 않는 통화 코드가 있습니다: {}", code);
                }
            }
            log.info("환율 동기화 배치를 성공적으로 완료했습니다.");
        } catch (Exception e) {
            log.error("환율 동기화 배치 실행 중 오류 발생", e);
        }
    }
}

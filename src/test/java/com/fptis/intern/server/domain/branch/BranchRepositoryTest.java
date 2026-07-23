package com.fptis.intern.server.domain.branch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * @DataJpaTest + 실 MySQL 컨테이너로 Flyway V4~V6 마이그레이션이 Branch/BranchCurrencyRate
 * 엔티티와 어긋나지 않는지(ddl-auto=validate) 검증한다.
 */
@Testcontainers
@DataJpaTest
@EnableJpaAuditing
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BranchRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private BranchCurrencyRateRepository branchCurrencyRateRepository;

    @Test
    void savesBranchWithSupportedCurrenciesAndFindsOnlyActiveOnes() {
        Branch active = branchRepository.save(Branch.builder()
                .name("명동 환전센터")
                .address("서울 중구 명동길 1")
                .latitude(37.5665)
                .longitude(126.9780)
                .phone("02-123-4567")
                .businessHours("평일 09:00-18:00, 토 09:00-13:00")
                .timeSlotCapacity(4)
                .supportedCurrencies(List.of("USD", "JPY", "EUR"))
                .build());

        Branch inactive = branchRepository.save(Branch.builder()
                .name("폐업 지점")
                .address("서울 어딘가")
                .latitude(37.0)
                .longitude(127.0)
                .phone("02-000-0000")
                .businessHours("평일 09:00-18:00")
                .timeSlotCapacity(2)
                .build());
        inactive.update(null, null, null, null, null, null, null, null, null, false);
        branchRepository.save(inactive);

        assertThat(branchRepository.findByActiveTrue())
                .extracting(Branch::getId)
                .containsExactly(active.getId());

        Branch reloaded = branchRepository.findById(active.getId()).orElseThrow();
        assertThat(reloaded.getSupportedCurrencies()).containsExactlyInAnyOrder("USD", "JPY", "EUR");
    }

    @Test
    void upsertsBranchCurrencyRatePerBranchAndCurrency() {
        Branch branch = branchRepository.save(Branch.builder()
                .name("명동 환전센터")
                .address("서울 중구 명동길 1")
                .latitude(37.5665)
                .longitude(126.9780)
                .phone("02-123-4567")
                .businessHours("평일 09:00-18:00")
                .timeSlotCapacity(4)
                .build());

        BranchCurrencyRate rate = branchCurrencyRateRepository.save(BranchCurrencyRate.builder()
                .branchId(branch.getId())
                .currencyCode("USD")
                .preferentialRate(0.5)
                .reservationOnlyStock(2000)
                .build());

        assertThat(branchCurrencyRateRepository.findByBranchIdAndCurrencyCode(branch.getId(), "USD"))
                .isPresent()
                .get()
                .extracting(BranchCurrencyRate::getId)
                .isEqualTo(rate.getId());

        assertThat(branchCurrencyRateRepository.findByBranchIdIn(List.of(branch.getId())))
                .hasSize(1);
    }
}

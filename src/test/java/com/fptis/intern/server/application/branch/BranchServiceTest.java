package com.fptis.intern.server.application.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchSortType;
import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.presentation.branch.dto.BranchCurrencyRateResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchDetailResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchSummaryResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * finalRate(기준 환율 × (1 − 우대율/100))와 isBestRateNearby(주변 5km 내 최저 매도 환율 여부, #21)를
 * 검증한다. 명동(37.5665, 126.9780) 기준 위도 0.02도 ≈ 2.2km(주변 반경 내), 0.1도 ≈ 11km(반경 밖)로
 * 좌표를 구성한다.
 */
@Testcontainers
@DataJpaTest
@EnableJpaAuditing
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BranchService.class)
class BranchServiceTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private BranchRepository branchRepository;
    @Autowired
    private BranchCurrencyRateRepository branchCurrencyRateRepository;
    @Autowired
    private CurrencyRepository currencyRepository;
    @Autowired
    private BranchService branchService;

    @BeforeEach
    void setUp() {
        saveCurrency("USD", "미국", 1370.0, 1400.0);
    }

    /**
     * V14 시드 마이그레이션이 USD 등 일부 통화를 이미 넣어두므로, 존재하면 테스트가 기대하는
     * 환율로 갱신하고 없으면 새로 만든다 — 시드 데이터 유무와 무관하게 결정적으로 동작해야 한다.
     */
    private Currency saveCurrency(String code, String country, double buyRate, double sellRate) {
        Currency currency = currencyRepository.findByCode(code)
                .orElseGet(() -> Currency.builder().code(code).country(country).buyRate(buyRate).sellRate(sellRate).build());
        currency.updateRates(buyRate, sellRate);
        return currencyRepository.save(currency);
    }

    private Branch saveBranch(String name, double latitude, double longitude) {
        return branchRepository.save(Branch.builder()
                .name(name)
                .address("서울 중구 명동길 1")
                .latitude(latitude)
                .longitude(longitude)
                .phone("02-123-4567")
                .businessHours("평일 09:00-18:00")
                .timeSlotCapacity(4)
                .build());
    }

    private void saveRate(Branch branch, String currencyCode, double preferentialRate) {
        branchCurrencyRateRepository.save(BranchCurrencyRate.builder()
                .branchId(branch.getId())
                .currencyCode(currencyCode)
                .preferentialRate(preferentialRate)
                .reservationOnlyStock(1000)
                .build());
    }

    @Test
    void listBranchesComputesFinalRateAndSortsAscending() {
        Branch cheaper = saveBranch("우대율 높은 지점", 37.5665, 126.9780);
        saveRate(cheaper, "USD", 2.0); // finalRate = 1400 * (1 - 0.02) = 1372.0

        Branch pricier = saveBranch("우대율 낮은 지점", 37.5, 127.0);
        saveRate(pricier, "USD", 0.5); // finalRate = 1400 * (1 - 0.005) = 1393.0

        List<BranchSummaryResponse> result = branchService.listBranches("USD", null, null, BranchSortType.RATE);

        assertThat(result).extracting(BranchSummaryResponse::id)
                .containsExactly(cheaper.getId(), pricier.getId());
        assertThat(result.get(0).finalRate()).isCloseTo(1372.0, within(0.001));
        assertThat(result.get(1).finalRate()).isCloseTo(1393.0, within(0.001));
    }

    @Test
    void listBranchesFinalRateIsNullWithoutCurrencyParam() {
        Branch branch = saveBranch("명동 환전센터", 37.5665, 126.9780);
        saveRate(branch, "USD", 1.0);

        List<BranchSummaryResponse> result = branchService.listBranches(null, null, null, BranchSortType.RATE);

        assertThat(result).extracting(BranchSummaryResponse::finalRate).containsOnlyNulls();
    }

    @Test
    void getBranchComputesFinalRatePerCurrency() {
        Currency jpy = saveCurrency("JPY", "일본", 8.8, 9.2);

        Branch branch = saveBranch("명동 환전센터", 37.5665, 126.9780);
        saveRate(branch, "USD", 1.0); // 1400 * 0.99 = 1386.0
        saveRate(branch, "JPY", 2.0); // 9.2 * 0.98 = 9.016

        BranchDetailResponse detail = branchService.getBranch(branch.getId());

        assertThat(detail.currencies())
                .extracting(BranchCurrencyRateResponse::currencyCode, BranchCurrencyRateResponse::finalRate)
                .containsExactlyInAnyOrder(tuple("USD", 1386.0), tuple("JPY", 9.016));
    }

    @Test
    void isBestRateNearbyTrueWhenLowestAmongNearbyBranches() {
        Branch target = saveBranch("최저가 지점", 37.5665, 126.9780);
        saveRate(target, "USD", 2.0); // finalRate 1372.0

        Branch nearbyWorse = saveBranch("근처 비싼 지점", 37.5865, 126.9780); // 위도 +0.02 ≈ 2.2km
        saveRate(nearbyWorse, "USD", 0.5); // finalRate 1393.0

        Branch farBetter = saveBranch("먼 곳 더 싼 지점", 37.6665, 126.9780); // 위도 +0.1 ≈ 11km, 반경 밖
        saveRate(farBetter, "USD", 5.0); // finalRate 1330.0 (더 낮지만 반경 밖이라 무시돼야 한다)

        BranchDetailResponse detail = branchService.getBranch(target.getId());

        assertThat(detail.isBestRateNearby()).isTrue();
    }

    @Test
    void isBestRateNearbyFalseWhenNearbyBranchHasBetterRate() {
        Branch target = saveBranch("보통 지점", 37.5665, 126.9780);
        saveRate(target, "USD", 0.5); // finalRate 1393.0

        Branch nearbyBetter = saveBranch("근처 최저가 지점", 37.5865, 126.9780); // ≈ 2.2km
        saveRate(nearbyBetter, "USD", 2.0); // finalRate 1372.0

        BranchDetailResponse detail = branchService.getBranch(target.getId());

        assertThat(detail.isBestRateNearby()).isFalse();
    }

    @Test
    void isBestRateNearbyFalseWhenBranchHasNoCurrencyRates() {
        Branch target = saveBranch("환율 미설정 지점", 37.5665, 126.9780);

        BranchDetailResponse detail = branchService.getBranch(target.getId());

        assertThat(detail.isBestRateNearby()).isFalse();
        assertThat(detail.currencies()).isEmpty();
    }
}

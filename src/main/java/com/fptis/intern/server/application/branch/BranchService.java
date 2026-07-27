package com.fptis.intern.server.application.branch;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchSortType;
import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.global.config.BranchRecommendationProperties;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.global.util.GeoUtil;
import com.fptis.intern.server.presentation.branch.dto.BranchCreateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchCurrencyRateResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchDetailResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchRateUpdateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchRecommendation;
import com.fptis.intern.server.presentation.branch.dto.BranchRecommendationResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchSummaryResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchUpdateRequest;
import com.fptis.intern.server.presentation.branch.dto.ScoreBreakdown;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchService {

    /**
     * BranchDetail.isBestRateNearby(PRD §18.2)의 "주변" 반경 — 이 엔드포인트는 사용자 위치 파라미터가 없어
     * /branches/recommend의 기본 반경(radius_km=5)을 그대로 재사용한다.
     */
    private static final double NEARBY_RADIUS_KM = 5.0;

    private final BranchRepository branchRepository;
    private final BranchCurrencyRateRepository branchCurrencyRateRepository;
    private final CurrencyRepository currencyRepository;
    private final BranchRecommendationProperties recommendationProperties;

    public List<BranchSummaryResponse> listBranches(String currencyCode, Double latitude, Double longitude,
                                                      BranchSortType sort) {
        List<Branch> branches = branchRepository.findActiveBranches();
        List<Long> branchIds = branches.stream().map(Branch::getId).toList();
        Map<Long, List<BranchCurrencyRate>> ratesByBranch = branchCurrencyRateRepository.findRatesByBranches(branchIds)
                .stream()
                .collect(Collectors.groupingBy(BranchCurrencyRate::getBranchId));

        // 목록 조회는 currency 파라미터 하나에 대해서만 finalRate를 계산하므로, 지점 수와 무관하게 조회 1건이면 충분하다.
        Currency currency = currencyCode != null ? currencyRepository.findByCode(currencyCode).orElse(null) : null;

        boolean hasLocation = latitude != null && longitude != null;
        LocalDateTime now = LocalDateTime.now();

        List<BranchSummaryResponse> result = branches.stream()
                .map(branch -> toSummary(branch, ratesByBranch.getOrDefault(branch.getId(), List.of()),
                        currencyCode, currency, hasLocation ? latitude : null, hasLocation ? longitude : null, now))
                .collect(Collectors.toCollection(ArrayList::new));

        sort(result, sort, currencyCode, hasLocation);
        return result;
    }

    public BranchDetailResponse getBranch(Long id) {
        Branch branch = getBranchOrThrow(id);
        List<BranchCurrencyRate> rates = branchCurrencyRateRepository.findRatesByBranch(id);
        return buildDetail(branch, rates);
    }

    /**
     * PRD §18.2: 거리/환율/재고/예약 가능성 4개 요소의 가중 합산 점수로 근처 환전소를 랭킹한다.
     * radiusKm 밖이거나 currencyCode를 취급하지 않는 지점은 후보에서 제외한다. 각 하위 점수는
     * 이번 검색 결과(후보 집합) 내에서 0~1로 상대 정규화한다 — 절대 기준값(예: "표준 재고량")이
     * 정의돼 있지 않아서다. isBestRateNearby는 이 검색 반경/통화 기준 후보 전체(top_n 절단 전) 중
     * 최저 finalRate 여부로 판단한다.
     */
    public BranchRecommendationResponse recommendBranches(double latitude, double longitude, String currencyCode,
                                                            double radiusKm, int topN) {
        Currency currency = currencyRepository.findByCode(currencyCode)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.CURRENCY_NOT_FOUND));

        List<Branch> branches = branchRepository.findActiveBranches();
        List<Long> branchIds = branches.stream().map(Branch::getId).toList();
        Map<Long, BranchCurrencyRate> rateByBranch = branchCurrencyRateRepository.findRatesByBranches(branchIds)
                .stream()
                .filter(rate -> rate.getCurrencyCode().equalsIgnoreCase(currencyCode))
                .collect(Collectors.toMap(BranchCurrencyRate::getBranchId, Function.identity()));

        List<RecommendationCandidate> candidates = branches.stream()
                .map(branch -> toCandidate(branch, rateByBranch.get(branch.getId()), currency, latitude, longitude, radiusKm))
                .filter(Objects::nonNull)
                .toList();

        if (candidates.isEmpty()) {
            return BranchRecommendationResponse.of(List.of());
        }

        double minFinalRate = candidates.stream().mapToDouble(RecommendationCandidate::finalRate).min().orElseThrow();
        double maxFinalRate = candidates.stream().mapToDouble(RecommendationCandidate::finalRate).max().orElseThrow();
        double minStock = candidates.stream().mapToDouble(c -> c.rate().getReservationOnlyStock()).min().orElseThrow();
        double maxStock = candidates.stream().mapToDouble(c -> c.rate().getReservationOnlyStock()).max().orElseThrow();
        int minCapacity = candidates.stream().mapToInt(c -> c.branch().getTimeSlotCapacity()).min().orElseThrow();
        int maxCapacity = candidates.stream().mapToInt(c -> c.branch().getTimeSlotCapacity()).max().orElseThrow();

        List<BranchRecommendation> results = candidates.stream()
                .map(c -> toRecommendation(c, radiusKm, minFinalRate, maxFinalRate, minStock, maxStock, minCapacity, maxCapacity))
                .sorted(Comparator.comparingDouble(BranchRecommendation::totalScore).reversed()
                        .thenComparing(BranchRecommendation::distanceKm))
                .limit(Math.max(topN, 0))
                .toList();

        return BranchRecommendationResponse.of(results);
    }

    @Transactional
    public BranchCurrencyRateResponse updateBranchRate(Long branchId, BranchRateUpdateRequest request) {
        getBranchOrThrow(branchId);
        BranchCurrencyRate rate = branchCurrencyRateRepository
                .findRate(branchId, request.currencyCode())
                .orElseGet(() -> BranchCurrencyRate.builder()
                        .branchId(branchId)
                        .currencyCode(request.currencyCode())
                        .preferentialRate(0)
                        .reservationOnlyStock(0)
                        .build());
        rate.update(request.preferentialRate(), request.reservationOnlyStock());
        branchCurrencyRateRepository.save(rate);

        Double finalRate = currencyRepository.findByCode(rate.getCurrencyCode())
                .map(currency -> currency.calculateFinalRate(rate.getPreferentialRate()))
                .orElse(null);
        return BranchCurrencyRateResponse.from(rate, finalRate);
    }

    @Transactional
    public BranchDetailResponse createBranch(BranchCreateRequest request) {
        Branch branch = Branch.builder()
                .name(request.name())
                .address(request.address())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .phone(request.phone())
                .businessHours(request.businessHours())
                .pickupLocationDetail(request.pickupLocationDetail())
                .timeSlotCapacity(request.timeSlotCapacity())
                .supportedCurrencies(request.supportedCurrencies())
                .build();
        branchRepository.save(branch);
        return BranchDetailResponse.of(branch, List.of(), Map.of(), false);
    }

    @Transactional
    public BranchDetailResponse updateBranch(Long id, BranchUpdateRequest request) {
        Branch branch = getBranchOrThrow(id);
        branch.update(request.name(), request.address(), request.latitude(), request.longitude(), request.phone(),
                request.businessHours(), request.pickupLocationDetail(), request.timeSlotCapacity(),
                request.supportedCurrencies(), request.active());
        List<BranchCurrencyRate> rates = branchCurrencyRateRepository.findRatesByBranch(id);
        return buildDetail(branch, rates);
    }

    private BranchSummaryResponse toSummary(Branch branch, List<BranchCurrencyRate> rates, String currencyCode,
                                             Currency currency, Double latitude, Double longitude, LocalDateTime now) {
        Double distanceKm = (latitude != null && longitude != null)
                ? GeoUtil.distanceKm(latitude, longitude, branch.getLatitude(), branch.getLongitude())
                : null;

        Double preferentialRate = null;
        Double finalRate = null;
        boolean reservationAvailable;
        if (currencyCode != null) {
            BranchCurrencyRate rate = rates.stream()
                    .filter(r -> r.getCurrencyCode().equalsIgnoreCase(currencyCode))
                    .findFirst()
                    .orElse(null);
            preferentialRate = rate != null ? rate.getPreferentialRate() : null;
            finalRate = (rate != null && currency != null) ? currency.calculateFinalRate(rate.getPreferentialRate()) : null;
            reservationAvailable = rate != null && rate.hasStock();
        } else {
            reservationAvailable = rates.stream().anyMatch(BranchCurrencyRate::hasStock);
        }

        return BranchSummaryResponse.of(branch, distanceKm, branch.isOpenNow(now), preferentialRate, finalRate,
                reservationAvailable);
    }

    /**
     * 지점이 취급하는 통화별 finalRate와, 그 중 하나라도 주변(NEARBY_RADIUS_KM) 활성 지점 대비 최저 매도
     * 환율이면 true가 되는 isBestRateNearby를 계산한다. Currency 조회는 이 지점이 취급하는 통화 코드
     * 집합에 대해 findByCodeIn으로 한 번만 배치 조회해 N+1을 피한다.
     */
    private BranchDetailResponse buildDetail(Branch branch, List<BranchCurrencyRate> rates) {
        List<String> currencyCodes = rates.stream().map(BranchCurrencyRate::getCurrencyCode).distinct().toList();
        Map<String, Currency> currencyByCode = currencyRepository.findByCodeIn(currencyCodes).stream()
                .collect(Collectors.toMap(Currency::getCode, Function.identity()));

        Map<String, Double> finalRateByCurrency = rates.stream()
                .filter(rate -> currencyByCode.containsKey(rate.getCurrencyCode()))
                .collect(Collectors.toMap(BranchCurrencyRate::getCurrencyCode,
                        rate -> currencyByCode.get(rate.getCurrencyCode()).calculateFinalRate(rate.getPreferentialRate())));

        boolean isBestRateNearby = isBestRateNearby(branch, finalRateByCurrency, currencyByCode);
        return BranchDetailResponse.of(branch, rates, finalRateByCurrency, isBestRateNearby);
    }

    private boolean isBestRateNearby(Branch branch, Map<String, Double> finalRateByCurrency,
                                      Map<String, Currency> currencyByCode) {
        if (finalRateByCurrency.isEmpty()) {
            return false;
        }

        List<Long> nearbyBranchIds = branchRepository.findActiveBranches().stream()
                .filter(other -> !other.getId().equals(branch.getId()))
                .filter(other -> GeoUtil.distanceKm(branch.getLatitude(), branch.getLongitude(),
                        other.getLatitude(), other.getLongitude()) <= NEARBY_RADIUS_KM)
                .map(Branch::getId)
                .toList();

        Map<String, List<BranchCurrencyRate>> nearbyRatesByCurrency = branchCurrencyRateRepository
                .findRatesByBranches(nearbyBranchIds).stream()
                .collect(Collectors.groupingBy(BranchCurrencyRate::getCurrencyCode));

        return finalRateByCurrency.entrySet().stream().anyMatch(entry -> {
            String currencyCode = entry.getKey();
            double thisFinalRate = entry.getValue();
            Currency currency = currencyByCode.get(currencyCode);
            double minNearbyFinalRate = nearbyRatesByCurrency.getOrDefault(currencyCode, List.of()).stream()
                    .mapToDouble(rate -> currency.calculateFinalRate(rate.getPreferentialRate()))
                    .min()
                    .orElse(Double.MAX_VALUE);
            return thisFinalRate <= minNearbyFinalRate;
        });
    }

    /**
     * finalRate(기준 환율 × (1 − 우대율))가 낮을수록 고객에게 유리한 조건이라 오름차순 정렬한다.
     */
    private void sort(List<BranchSummaryResponse> summaries, BranchSortType sort, String currencyCode, boolean hasLocation) {
        if (sort == BranchSortType.DISTANCE && hasLocation) {
            summaries.sort(Comparator.comparing(BranchSummaryResponse::distanceKm,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        } else if (sort == BranchSortType.RATE && currencyCode != null) {
            summaries.sort(Comparator.comparing(BranchSummaryResponse::finalRate,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }
    }

    private RecommendationCandidate toCandidate(Branch branch, BranchCurrencyRate rate, Currency currency,
                                                 double latitude, double longitude, double radiusKm) {
        if (rate == null) {
            return null;
        }
        double distanceKm = GeoUtil.distanceKm(latitude, longitude, branch.getLatitude(), branch.getLongitude());
        if (distanceKm > radiusKm) {
            return null;
        }
        double finalRate = currency.calculateFinalRate(rate.getPreferentialRate());
        return new RecommendationCandidate(branch, rate, distanceKm, finalRate);
    }

    private BranchRecommendation toRecommendation(RecommendationCandidate candidate, double radiusKm,
                                                    double minFinalRate, double maxFinalRate,
                                                    double minStock, double maxStock,
                                                    int minCapacity, int maxCapacity) {
        double distanceScore = radiusKm <= 0
                ? (candidate.distanceKm() <= 0 ? 1.0 : 0.0)
                : clamp(1 - candidate.distanceKm() / radiusKm);
        double rateScore = normalize(maxFinalRate - candidate.finalRate(), maxFinalRate - minFinalRate);
        double availabilityScore = normalize(candidate.rate().getReservationOnlyStock() - minStock, maxStock - minStock);
        double reservationScore = normalize(candidate.branch().getTimeSlotCapacity() - minCapacity, maxCapacity - minCapacity);

        double totalScore = recommendationProperties.distanceWeight() * distanceScore
                + recommendationProperties.rateWeight() * rateScore
                + recommendationProperties.availabilityWeight() * availabilityScore
                + recommendationProperties.reservationWeight() * reservationScore;

        boolean isBestRateNearby = candidate.finalRate() <= minFinalRate;
        ScoreBreakdown breakdown = new ScoreBreakdown(distanceScore, rateScore, availabilityScore, reservationScore);

        return BranchRecommendation.of(candidate.branch(), candidate.distanceKm(),
                candidate.rate().getPreferentialRate(), candidate.finalRate(), candidate.rate().hasStock(),
                totalScore, breakdown, isBestRateNearby);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    /**
     * 후보 집합 내 상대 정규화 — 절대 기준값이 없어 min-max로 0~1 스케일링한다.
     * 모든 후보가 동일하면(range=0) 비교할 게 없으므로 만점(1.0)으로 취급한다.
     */
    private double normalize(double diff, double range) {
        return range == 0 ? 1.0 : clamp(diff / range);
    }

    private record RecommendationCandidate(Branch branch, BranchCurrencyRate rate, double distanceKm, double finalRate) {
    }

    private Branch getBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BRANCH_NOT_FOUND));
    }
}

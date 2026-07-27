package com.fptis.intern.server.application.branch;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchSortType;
import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.global.util.GeoUtil;
import com.fptis.intern.server.presentation.branch.dto.BranchCreateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchCurrencyRateResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchDetailResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchRateUpdateRequest;
import com.fptis.intern.server.presentation.branch.dto.BranchSummaryResponse;
import com.fptis.intern.server.presentation.branch.dto.BranchUpdateRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
     * /branches/recommendations의 기본 반경(radius_km=5)을 그대로 재사용한다.
     */
    private static final double NEARBY_RADIUS_KM = 5.0;

    private final BranchRepository branchRepository;
    private final BranchCurrencyRateRepository branchCurrencyRateRepository;
    private final CurrencyRepository currencyRepository;

    public List<BranchSummaryResponse> listBranches(String currencyCode, Double latitude, Double longitude,
                                                      BranchSortType sort) {
        List<Branch> branches = branchRepository.findActiveBranches();
        List<Long> branchIds = branches.stream().map(Branch::getId).toList();
        Map<Long, List<BranchCurrencyRate>> ratesByBranch = branchCurrencyRateRepository.findRatesByBranches(branchIds)
                .stream()
                .collect(Collectors.groupingBy(BranchCurrencyRate::getBranchId));

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
     * finalRate(기준 환율 × (1 − 우대율/100))가 낮을수록 고객에게 유리한 조건이라 오름차순 정렬한다.
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

    private Branch getBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BRANCH_NOT_FOUND));
    }
}

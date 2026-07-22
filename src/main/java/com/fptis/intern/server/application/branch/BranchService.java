package com.fptis.intern.server.application.branch;

import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.branch.BranchSortType;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchCurrencyRateRepository branchCurrencyRateRepository;

    public List<BranchSummaryResponse> listBranches(String currencyCode, Double latitude, Double longitude,
                                                      BranchSortType sort) {
        List<Branch> branches = branchRepository.findActiveBranches();
        List<Long> branchIds = branches.stream().map(Branch::getId).toList();
        Map<Long, List<BranchCurrencyRate>> ratesByBranch = branchCurrencyRateRepository.findRatesByBranches(branchIds)
                .stream()
                .collect(Collectors.groupingBy(BranchCurrencyRate::getBranchId));

        boolean hasLocation = latitude != null && longitude != null;
        LocalDateTime now = LocalDateTime.now();

        List<BranchSummaryResponse> result = branches.stream()
                .map(branch -> toSummary(branch, ratesByBranch.getOrDefault(branch.getId(), List.of()),
                        currencyCode, hasLocation ? latitude : null, hasLocation ? longitude : null, now))
                .collect(Collectors.toCollection(ArrayList::new));

        sort(result, sort, currencyCode, hasLocation);
        return result;
    }

    public BranchDetailResponse getBranch(Long id) {
        Branch branch = getBranchOrThrow(id);
        List<BranchCurrencyRate> rates = branchCurrencyRateRepository.findRatesByBranch(id);
        return BranchDetailResponse.of(branch, rates);
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
        return BranchCurrencyRateResponse.from(rate);
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
        return BranchDetailResponse.of(branch, List.of());
    }

    @Transactional
    public BranchDetailResponse updateBranch(Long id, BranchUpdateRequest request) {
        Branch branch = getBranchOrThrow(id);
        branch.update(request.name(), request.address(), request.latitude(), request.longitude(), request.phone(),
                request.businessHours(), request.pickupLocationDetail(), request.timeSlotCapacity(),
                request.supportedCurrencies(), request.active());
        List<BranchCurrencyRate> rates = branchCurrencyRateRepository.findRatesByBranch(id);
        return BranchDetailResponse.of(branch, rates);
    }

    private BranchSummaryResponse toSummary(Branch branch, List<BranchCurrencyRate> rates, String currencyCode,
                                             Double latitude, Double longitude, LocalDateTime now) {
        Double distanceKm = (latitude != null && longitude != null)
                ? GeoUtil.distanceKm(latitude, longitude, branch.getLatitude(), branch.getLongitude())
                : null;

        Double preferentialRate = null;
        boolean reservationAvailable;
        if (currencyCode != null) {
            BranchCurrencyRate rate = rates.stream()
                    .filter(r -> r.getCurrencyCode().equalsIgnoreCase(currencyCode))
                    .findFirst()
                    .orElse(null);
            preferentialRate = rate != null ? rate.getPreferentialRate() : null;
            reservationAvailable = rate != null && rate.hasStock();
        } else {
            reservationAvailable = rates.stream().anyMatch(BranchCurrencyRate::hasStock);
        }

        return BranchSummaryResponse.of(branch, distanceKm, branch.isOpenNow(now), preferentialRate, reservationAvailable);
    }

    /**
     * 실제 finalRate 기준 정렬은 Currency 도메인이 없어 preferentialRate로 대체한다 — #21에서 교체 예정.
     */
    private void sort(List<BranchSummaryResponse> summaries, BranchSortType sort, String currencyCode, boolean hasLocation) {
        if (sort == BranchSortType.DISTANCE && hasLocation) {
            summaries.sort(Comparator.comparing(BranchSummaryResponse::distanceKm,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        } else if (sort == BranchSortType.RATE && currencyCode != null) {
            summaries.sort(Comparator.comparing(BranchSummaryResponse::preferentialRate,
                    Comparator.nullsLast(Comparator.reverseOrder())));
        }
    }

    private Branch getBranchOrThrow(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.BRANCH_NOT_FOUND));
    }
}

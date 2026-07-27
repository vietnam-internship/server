package com.fptis.intern.server.application.recommendation;

import com.fptis.intern.server.application.ai.AiRecommendationClient;
import com.fptis.intern.server.domain.branch.Branch;
import com.fptis.intern.server.domain.branch.BranchCurrencyRate;
import com.fptis.intern.server.domain.branch.BranchCurrencyRateRepository;
import com.fptis.intern.server.domain.branch.BranchRepository;
import com.fptis.intern.server.domain.currency.Currency;
import com.fptis.intern.server.domain.currency.CurrencyRepository;
import com.fptis.intern.server.domain.recommendation.BranchRecommendation;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationClick;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationClickRepository;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationItem;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationItemRepository;
import com.fptis.intern.server.domain.recommendation.BranchRecommendationRepository;
import com.fptis.intern.server.domain.recommendation.RecommendationStatus;
import com.fptis.intern.server.global.exception.BusinessErrorCode;
import com.fptis.intern.server.global.exception.BusinessException;
import com.fptis.intern.server.global.util.GeoUtil;
import com.fptis.intern.server.presentation.ai.dto.BranchRecommendationPushRequest;
import com.fptis.intern.server.presentation.ai.dto.RankedBranchItem;
import com.fptis.intern.server.presentation.branch.dto.ScoreBreakdown;
import com.fptis.intern.server.presentation.recommendation.dto.BranchRecommendationCreateRequest;
import com.fptis.intern.server.presentation.recommendation.dto.BranchRecommendationQueryResponse;
import java.math.BigDecimal;
import java.util.HashMap;
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
public class BranchRecommendationService {

    // domain.recommendation.BranchRecommendation (entity)이 import되어 있다.
    // presentation.branch.dto.BranchRecommendation (DTO)는 FQCN으로만 참조한다.

    private final BranchRecommendationRepository recommendationRepository;
    private final BranchRecommendationItemRepository itemRepository;
    private final BranchRecommendationClickRepository clickRepository;
    private final BranchRepository branchRepository;
    private final BranchCurrencyRateRepository branchCurrencyRateRepository;
    private final CurrencyRepository currencyRepository;
    private final AiRecommendationClient aiRecommendationClient;

    @Transactional
    public Long createRecommendation(BranchRecommendationCreateRequest request, Long userId) {
        BranchRecommendation session = BranchRecommendation.builder()
                .userId(userId)
                .currency(request.currency().toUpperCase())
                .amount(BigDecimal.valueOf(request.amount()))
                .latitude(BigDecimal.valueOf(request.latitude()))
                .longitude(BigDecimal.valueOf(request.longitude()))
                .radiusKm(request.radiusKm())
                .build();
        recommendationRepository.save(session);

        aiRecommendationClient.triggerBranchRanking(
                session.getId(), session.getLatitude(), session.getLongitude(),
                session.getRadiusKm(), session.getCurrency(), session.getAmount()
        );

        return session.getId();
    }

    @Transactional
    public void receiveAiResult(BranchRecommendationPushRequest request) {
        BranchRecommendation session = getSessionOrThrow(request.sessionId());

        List<BranchRecommendationItem> items = request.rankedBranches().stream()
                .map(ranked -> toItem(session.getId(), ranked))
                .toList();
        itemRepository.saveAll(items);
        session.complete();
    }

    public BranchRecommendationQueryResponse getRecommendation(Long sessionId) {
        BranchRecommendation session = getSessionOrThrow(sessionId);

        if (session.getStatus() != RecommendationStatus.COMPLETED) {
            return BranchRecommendationQueryResponse.of(session.getStatus().name(), List.of());
        }

        List<BranchRecommendationItem> items = itemRepository.findByRecommendationIdOrderByRankingAsc(sessionId);
        if (items.isEmpty()) {
            return BranchRecommendationQueryResponse.of(RecommendationStatus.COMPLETED.name(), List.of());
        }

        List<Long> branchIds = items.stream().map(BranchRecommendationItem::getBranchId).toList();

        Map<Long, Branch> branchById = branchRepository.findAllById(branchIds).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));

        Map<Long, BranchCurrencyRate> rateByBranch = branchCurrencyRateRepository.findRatesByBranches(branchIds)
                .stream()
                .filter(r -> r.getCurrencyCode().equalsIgnoreCase(session.getCurrency()))
                .collect(Collectors.toMap(BranchCurrencyRate::getBranchId, Function.identity()));

        Currency currency = currencyRepository.findByCode(session.getCurrency()).orElse(null);

        Map<Long, Double> finalRateByBranchId = new HashMap<>();
        for (BranchRecommendationItem item : items) {
            BranchCurrencyRate rate = rateByBranch.get(item.getBranchId());
            if (rate != null && currency != null) {
                finalRateByBranchId.put(item.getBranchId(), currency.calculateFinalRate(rate.getPreferentialRate()));
            }
        }

        double minFinalRate = finalRateByBranchId.values().stream()
                .mapToDouble(Double::doubleValue).min().orElse(Double.MAX_VALUE);

        double sessionLat = session.getLatitude().doubleValue();
        double sessionLng = session.getLongitude().doubleValue();

        List<com.fptis.intern.server.presentation.branch.dto.BranchRecommendation> results = items.stream()
                .map(item -> {
                    Branch branch = branchById.get(item.getBranchId());
                    if (branch == null) {
                        return null;
                    }
                    BranchCurrencyRate rate = rateByBranch.get(item.getBranchId());
                    Double finalRate = finalRateByBranchId.get(item.getBranchId());
                    Double preferentialRate = rate != null ? rate.getPreferentialRate() : null;
                    boolean reservationAvailable = rate != null && rate.hasStock();
                    boolean isBestRateNearby = finalRate != null && finalRate <= minFinalRate;
                    double distanceKm = GeoUtil.distanceKm(sessionLat, sessionLng,
                            branch.getLatitude(), branch.getLongitude());
                    double score = item.getScore() != null ? item.getScore().doubleValue() : 0.0;

                    return com.fptis.intern.server.presentation.branch.dto.BranchRecommendation.of(
                            branch, item.getRanking(), distanceKm, preferentialRate, finalRate,
                            reservationAvailable, score, buildBreakdown(item), isBestRateNearby
                    );
                })
                .filter(Objects::nonNull)
                .toList();

        return BranchRecommendationQueryResponse.of(RecommendationStatus.COMPLETED.name(), results);
    }

    @Transactional
    public void recordClick(Long itemId) {
        itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.RECOMMENDATION_ITEM_NOT_FOUND));
        clickRepository.save(BranchRecommendationClick.of(itemId));
    }

    private BranchRecommendationItem toItem(Long sessionId, RankedBranchItem ranked) {
        return BranchRecommendationItem.builder()
                .recommendationId(sessionId)
                .branchId(ranked.branchId())
                .ranking(ranked.ranking())
                .score(BigDecimal.valueOf(ranked.score()))
                .distanceScore(ranked.breakdown() != null
                        ? BigDecimal.valueOf(ranked.breakdown().distanceScore()) : null)
                .rateScore(ranked.breakdown() != null
                        ? BigDecimal.valueOf(ranked.breakdown().rateScore()) : null)
                .availabilityScore(ranked.breakdown() != null
                        ? BigDecimal.valueOf(ranked.breakdown().availabilityScore()) : null)
                .reservationScore(ranked.breakdown() != null
                        ? BigDecimal.valueOf(ranked.breakdown().reservationScore()) : null)
                .build();
    }

    private ScoreBreakdown buildBreakdown(BranchRecommendationItem item) {
        if (item.getDistanceScore() == null) {
            return null;
        }
        return new ScoreBreakdown(
                item.getDistanceScore().doubleValue(),
                item.getRateScore() != null ? item.getRateScore().doubleValue() : 0.0,
                item.getAvailabilityScore() != null ? item.getAvailabilityScore().doubleValue() : 0.0,
                item.getReservationScore() != null ? item.getReservationScore().doubleValue() : 0.0
        );
    }

    private BranchRecommendation getSessionOrThrow(Long sessionId) {
        return recommendationRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.RECOMMENDATION_NOT_FOUND));
    }
}

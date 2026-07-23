package com.fptis.intern.server.domain.branch;

import com.fptis.intern.server.global.base.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "branches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Branch extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "business_hours", nullable = false, length = 255)
    private String businessHours;

    @Column(name = "pickup_location_detail", length = 255)
    private String pickupLocationDetail;

    @Column(name = "time_slot_capacity", nullable = false)
    private int timeSlotCapacity;

    @Column(nullable = false)
    private boolean active;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "branch_supported_currencies", joinColumns = @JoinColumn(name = "branch_id"))
    @Column(name = "currency_code", nullable = false, length = 10)
    @OrderBy("currencyCode ASC")
    private List<String> supportedCurrencies = new ArrayList<>();

    @Builder
    private Branch(String name, String address, double latitude, double longitude, String phone,
                    String businessHours, String pickupLocationDetail, int timeSlotCapacity,
                    List<String> supportedCurrencies) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.businessHours = businessHours;
        this.pickupLocationDetail = pickupLocationDetail;
        this.timeSlotCapacity = timeSlotCapacity;
        this.active = true;
        this.supportedCurrencies = supportedCurrencies != null ? new ArrayList<>(supportedCurrencies) : new ArrayList<>();
    }

    public void update(String name, String address, Double latitude, Double longitude, String phone,
                        String businessHours, String pickupLocationDetail, Integer timeSlotCapacity,
                        List<String> supportedCurrencies, Boolean active) {
        if (name != null) {
            this.name = name;
        }
        if (address != null) {
            this.address = address;
        }
        if (latitude != null) {
            this.latitude = latitude;
        }
        if (longitude != null) {
            this.longitude = longitude;
        }
        if (phone != null) {
            this.phone = phone;
        }
        if (businessHours != null) {
            this.businessHours = businessHours;
        }
        if (pickupLocationDetail != null) {
            this.pickupLocationDetail = pickupLocationDetail;
        }
        if (timeSlotCapacity != null) {
            this.timeSlotCapacity = timeSlotCapacity;
        }
        if (supportedCurrencies != null) {
            this.supportedCurrencies.clear();
            this.supportedCurrencies.addAll(supportedCurrencies);
        }
        if (active != null) {
            this.active = active;
        }
    }

    public boolean isOpenNow(LocalDateTime at) {
        return BusinessHoursParser.isOpenAt(businessHours, at);
    }
}

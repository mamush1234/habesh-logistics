package com.habeshlist.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final com.habeshlist.service.MarketplaceService travel;

    public PublicController(com.habeshlist.service.MarketplaceService travel) {
        this.travel = travel;
    }

    @GetMapping("/travel")
    public java.util.List<com.habeshlist.dto.ApiDtos.PublicTravelDto> travel() {
        return travel.publicTrips();
    }

    @Value("${app.alexandria.address}")
    private String alexandriaAddress;
    @Value("${app.alexandria.hours}")
    private String alexandriaHours;
    @Value("${app.pricing.default-rate-per-kg}")
    private String defaultRate;
    @Value("${app.pricing.default-pickup-fee}")
    private String defaultPickupFee;
    @Value("${app.pricing.document-fee:10.00}")
    private String documentFee;
    @Value("${app.pricing.medicine-fee:15.00}")
    private String medicineFee;

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
            "alexandriaAddress", alexandriaAddress,
            "alexandriaHours", alexandriaHours,
            "defaultRatePerKg", defaultRate,
            "defaultPickupFee", defaultPickupFee,
            "documentFee", documentFee,
            "medicineFee", medicineFee,
            "brand", "HABESH LIST",
            "subtitle", "Habesh Logistics"
        );
    }
}

package com.habeshlist.controller;

import com.habeshlist.dto.MarketplaceDtos.*;
import com.habeshlist.service.MarketplaceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {
    private final MarketplaceService service;
    public MarketplaceController(MarketplaceService service) { this.service = service; }
    @GetMapping("/trips") public List<TripDto> trips() { return service.listTrips(); }
    @PostMapping("/trips") public TripDto trip(@Valid @RequestBody TripInput input) { return service.createTrip(input); }
    @GetMapping("/requests") public List<RequestDto> requests() { return service.listRequests(); }
    @PostMapping("/requests") public RequestDto request(@Valid @RequestBody RequestInput input) { return service.createRequest(input); }
    @GetMapping("/requests/{id}") public RequestDto request(@PathVariable UUID id) { return service.getRequest(id); }
    @GetMapping("/requests/{id}/product-photo") public Map<String, String> photo(@PathVariable UUID id) {
        return Map.of("photoDataUrl", service.productPhoto(id));
    }
    @GetMapping("/requests/{id}/matches") public List<MatchDto> requestMatches(@PathVariable UUID id) { return service.matchesForRequest(id); }
    @GetMapping("/trips/{id}/matches") public List<MatchDto> tripMatches(@PathVariable UUID id) { return service.matchesForTrip(id); }
    @PostMapping("/requests/{id}/offers") public RequestDto offer(@PathVariable UUID id, @Valid @RequestBody OfferInput input) {
        return service.sendOffer(id, input);
    }
    @PostMapping("/requests/{id}/offers/{offerId}/accept") public RequestDto accept(@PathVariable UUID id, @PathVariable UUID offerId) {
        return service.acceptOffer(id, offerId);
    }
    @PostMapping("/requests/{id}/offers/{offerId}/decline") public RequestDto decline(@PathVariable UUID id, @PathVariable UUID offerId) {
        return service.decideOffer(id, offerId, false);
    }
    @PostMapping("/requests/{id}/offers/{offerId}/withdraw") public RequestDto withdraw(@PathVariable UUID id, @PathVariable UUID offerId) {
        return service.decideOffer(id, offerId, true);
    }
    @PostMapping("/requests/{id}/demo-escrow") public RequestDto escrow(@PathVariable UUID id) { return service.fundDemoEscrow(id); }
    @PutMapping("/requests/{id}/status") public RequestDto status(@PathVariable UUID id, @Valid @RequestBody TransitionInput input) {
        return service.transition(id, input);
    }
    @PostMapping("/requests/{id}/rating") public RequestDto rating(@PathVariable UUID id, @Valid @RequestBody RatingInput input) {
        return service.rate(id, input.rating());
    }
    @PutMapping("/travelers/{id}/verification") public TravelerDto verify(@PathVariable UUID id, @Valid @RequestBody VerificationInput input) {
        return service.verify(id, input.level());
    }
}

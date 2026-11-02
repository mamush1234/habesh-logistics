package com.habeshlist.agent;

import com.habeshlist.agent.AgentDtos.*;
import com.habeshlist.agent.AgentTypes.CustomerType;
import com.habeshlist.dto.MarketplaceDtos.TripInput;
import com.habeshlist.entity.enums.CommissionStatus;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
@PreAuthorize("hasRole('AGENT')")
public class AgentPortalController {
    private final AgentPortalService service;

    public AgentPortalController(AgentPortalService service) { this.service = service; }

    @GetMapping("/dashboard")
    public DashboardDto dashboard() { return service.dashboard(); }

    @PostMapping("/customers")
    public ResponseEntity<CustomerCreatedDto> createCustomer(@Valid @RequestBody CreateCustomerInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.createCustomer(input));
    }

    @GetMapping("/customers")
    public PageDto<CustomerDto> customers(@RequestParam(required = false) CustomerType type,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer page) {
        return service.customers(type, q, page);
    }

    @GetMapping("/customers/{id}")
    public CustomerDto customer(@PathVariable UUID id) { return service.customer(id); }

    @PostMapping("/customers/{id}/packages")
    @ResponseStatus(HttpStatus.CREATED)
    public PackageDto createPackage(@PathVariable UUID id, @Valid @RequestBody PackageInput input) {
        return service.createPackage(id, input);
    }

    @PostMapping("/customers/{id}/packages/{packageId}/submit")
    public PackageDto submitDraft(@PathVariable UUID id, @PathVariable UUID packageId) {
        return service.submitDraft(id, packageId);
    }

    @PostMapping("/customers/{id}/trips")
    @ResponseStatus(HttpStatus.CREATED)
    public TripDto createTrip(@PathVariable UUID id, @Valid @RequestBody TripInput input) {
        return service.createTrip(id, input);
    }

    @GetMapping("/packages")
    public PageDto<PackageDto> packages(@RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) Integer page) {
        return service.packages(status, page);
    }

    @GetMapping("/trips")
    public PageDto<TripDto> trips(@RequestParam(required = false) Integer page) { return service.trips(page); }

    @GetMapping("/commissions")
    public CommissionPageDto commissions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) CommissionStatus status, @RequestParam(required = false) Integer page) {
        return service.commissions(from, to, status, page);
    }

    @GetMapping("/authorizations")
    public List<AuthorizationDto> authorizations() { return service.authorizations(); }

    @PostMapping("/authorizations")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthorizationDto grant(@Valid @RequestBody AuthorizationInput input) { return service.grant(input); }

    @DeleteMapping("/authorizations/{id}")
    public AuthorizationDto revoke(@PathVariable UUID id) { return service.revoke(id); }
}

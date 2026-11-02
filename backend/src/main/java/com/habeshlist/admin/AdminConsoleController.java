package com.habeshlist.admin;

import com.habeshlist.admin.AdminDtos.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

/** Admin console read models and settings. Existing /api/admin endpoints stay in AdminController. */
@RestController
@RequestMapping("/api/admin/console")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConsoleController {
    private final AdminConsoleService service;

    public AdminConsoleController(AdminConsoleService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public OverviewDto overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.overview(from, to);
    }

    @GetMapping("/users")
    public ListDto<UserRowDto> users(@RequestParam(required = false) String type, @RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page) {
        return service.users(type, q, status, page);
    }

    @GetMapping("/trips")
    public ListDto<TripRowDto> trips(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page) {
        return service.trips(q, status, page);
    }

    @GetMapping("/requests")
    public ListDto<RequestRowDto> requests(@RequestParam(defaultValue = "all") String view,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page) {
        return service.requests(view, q, status, page);
    }

    @PostMapping("/disputes/{id}/resolve")
    public RequestRowDto resolve(@PathVariable UUID id, @Valid @RequestBody ResolveDisputeInput input) {
        return service.resolveDispute(id, input);
    }

    @GetMapping("/payments")
    public PaymentsDto payments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page) {
        return service.payments(from, to, status, q, page);
    }

    @GetMapping("/commissions")
    public ListDto<CommissionRowDto> commissions(@RequestParam(required = false) String status,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) {
        return service.commissions(status, q, page);
    }

    @GetMapping("/reports")
    public ReportDto reports(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(from, to);
    }

    @GetMapping("/settings")
    public SettingsDto settings() {
        return service.settings();
    }

    @PutMapping("/settings")
    public SettingsDto updateSettings(@Valid @RequestBody SettingsInput input) {
        return service.updateSettings(input);
    }
}

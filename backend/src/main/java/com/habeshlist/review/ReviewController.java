package com.habeshlist.review;

import com.habeshlist.review.ReviewDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace")
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service) { this.service = service; }

    @GetMapping("/reviews")
    @PreAuthorize("hasAnyRole('SENDER', 'TRAVELER', 'AGENT')")
    public InboxDto inbox(@RequestParam(defaultValue = "0") int page) { return service.inbox(page); }

    @GetMapping("/requests/{id}/reviews")
    public BookingDto forPackage(@PathVariable UUID id) { return service.forPackage(id); }

    @PostMapping("/requests/{id}/reviews")
    @PreAuthorize("hasAnyRole('SENDER', 'TRAVELER', 'AGENT')")
    public BookingDto submit(@PathVariable UUID id, @Valid @RequestBody Input input) { return service.submit(id, input); }
}

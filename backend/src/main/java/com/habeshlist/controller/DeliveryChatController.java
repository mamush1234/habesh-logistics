package com.habeshlist.controller;

import com.habeshlist.dto.MarketplaceDtos.*;
import com.habeshlist.service.DeliveryChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace/requests/{id}/messages")
public class DeliveryChatController {
    private final DeliveryChatService service;
    public DeliveryChatController(DeliveryChatService service) { this.service = service; }
    @GetMapping public ChatDto list(@PathVariable UUID id, @RequestParam(required = false) Long before) {
        return service.list(id, before);
    }
    @PostMapping public MessageDto send(@PathVariable UUID id, @Valid @RequestBody MessageInput input) {
        return service.send(id, input);
    }
}

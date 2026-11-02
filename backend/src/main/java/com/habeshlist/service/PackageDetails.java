package com.habeshlist.service;

import com.habeshlist.dto.MarketplaceDtos.*;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.MarketplaceTypes.ItemType;
import com.habeshlist.exception.BadRequestException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public final class PackageDetails {
    private PackageDetails() {}

    public static void apply(DeliveryRequest request, RequestInput input) {
        if ((input.items() == null) != (input.receiver() == null))
            throw new BadRequestException("Provide both package items and receiver details");
        var items = input.items() == null
                ? List.of(new PackageItemInput(input.itemType(), input.weightKg(), input.description()))
                : input.items();
        if (items.isEmpty() || items.size() > 50)
            throw new BadRequestException("A package must contain between 1 and 50 items");
        BigDecimal total = items.stream().map(PackageItemInput::weightKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new BadRequestException("Total package weight must not exceed 99999999.99 kg");
        request.setWeightKg(total);
        request.setItemType(items.getFirst().category());
        request.getItems().clear();
        items.forEach(item -> request.getItems().add(PackageItem.builder().request(request)
                .category(item.category()).weightKg(item.weightKg()).description(item.description().strip()).build()));
        if (input.receiver() != null) {
            var receiver = input.receiver();
            request.setReceiver(Recipient.builder().name(receiver.name().strip()).phone(receiver.phone().strip())
                    .city(request.getDestinationCity()).address(receiver.address().strip())
                    .notes(receiver.notes() == null ? null : receiver.notes().strip()).build());
        }
    }

    public static List<PackageItemDto> items(DeliveryRequest request) {
        return request.getItems().isEmpty()
                ? List.of(new PackageItemDto(request.getItemType(), request.getWeightKg(), request.getDescription()))
                : request.getItems().stream().map(i -> new PackageItemDto(i.getCategory(), i.getWeightKg(), i.getDescription())).toList();
    }

    public static Set<ItemType> categories(DeliveryRequest request) {
        return items(request).stream().map(PackageItemDto::category).collect(Collectors.toSet());
    }

    public static ReceiverInput receiver(DeliveryRequest request) {
        var r = request.getReceiver();
        return r == null ? null : new ReceiverInput(r.getName(), r.getPhone(), r.getAddress(), r.getNotes());
    }
}

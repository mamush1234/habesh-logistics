package com.habeshlist.service;

import com.habeshlist.dto.MarketplaceDtos.*;
import com.habeshlist.entity.*;
import com.habeshlist.exception.BadRequestException;
import java.util.*;

public final class TripDetails {
    private TripDetails() {}

    public static void apply(MarketTrip trip, TripInput input) {
        trip.setHandoffLocation(input.handoffLocation() == null ? null : input.handoffLocation().strip());
        trip.setNotes(input.notes() == null ? null : input.notes().strip());
        if (input.itemPrices() == null) return;
        var categories = new HashSet<com.habeshlist.entity.enums.MarketplaceTypes.ItemType>();
        for (var price : input.itemPrices()) {
            if (!categories.add(price.category()))
                throw new BadRequestException("Each trip category can have only one price");
        }
        if (!categories.equals(input.allowedItems()))
            throw new BadRequestException("Provide a price for every allowed category and no other categories");
        input.itemPrices().forEach(price -> trip.getItemPrices().add(TripItemPrice.builder().trip(trip)
                .category(price.category()).price(price.price()).unit(price.unit()).build()));
    }

    public static List<TripItemPriceDto> prices(MarketTrip trip) {
        return trip.getItemPrices().stream().sorted(Comparator.comparing(TripItemPrice::getCategory))
                .map(p -> new TripItemPriceDto(p.getCategory(), p.getPrice(), p.getUnit())).toList();
    }
}

package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.GuestRequestDtos.ConvertInput;
import com.habeshlist.dto.MarketplaceDtos.TransitionInput;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.*;
import com.habeshlist.entity.enums.MarketplaceTypes.DeliveryStatus;
import com.habeshlist.exception.*;
import com.habeshlist.repository.DeliveryRequestRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class GuestShipmentConversionService {
    private final DeliveryRequestRepository requests;
    private final MarketplaceService marketplace;
    private final ShipmentService shipments;

    public GuestShipmentConversionService(DeliveryRequestRepository requests, MarketplaceService marketplace, ShipmentService shipments) {
        this.requests = requests;
        this.marketplace = marketplace;
        this.shipments = shipments;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ApiDtos.ShipmentDto convert(UUID id, ConvertInput input) {
        DeliveryRequest request = requests.findForUpdate(id).orElseThrow(() -> new NotFoundException("Request not found"));
        if (request.getSender() != null) throw new BadRequestException("Only guest requests can be converted to shipments");
        if (request.getConvertedShipment() != null) return ApiDtos.ShipmentDto.from(request.getConvertedShipment());
        if (!EnumSet.of(DeliveryStatus.REQUESTED, DeliveryStatus.MATCHED, DeliveryStatus.PAID_ESCROW).contains(request.getStatus()))
            throw new BadRequestException("Only guest requests awaiting pickup can be converted; closed, disputed or in-transit requests cannot be converted");
        marketplace.transition(id, new TransitionInput(DeliveryStatus.CANCELLED,
                "Moved to the operational shipment workflow; marketplace offers closed and any demo escrow refunded"));
        Shipment shipment = shipments.createFromGuest(request, input);
        if (request.getAcceptedOffer() != null) {
            User carrier = request.getAcceptedOffer().getTraveler();
            if (carrier.getRole() == Role.AGENT) shipment.setAssignedAgent(carrier);
            else shipment.setAssignedTraveler(carrier);
        }
        request.setConvertedShipment(shipment);
        return ApiDtos.ShipmentDto.from(shipment);
    }
}

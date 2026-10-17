package com.habeshlist.service;

import com.habeshlist.dto.PackageQueueDtos;
import com.habeshlist.entity.Shipment;
import com.habeshlist.entity.enums.Role;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.ShipmentRepository;
import com.habeshlist.security.SecurityUtils;
import com.habeshlist.security.ShipmentAccess;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

/** Read-only access to historical queue records and payment confirmations. */
@Service
public class PackageQueueService {
    private final ShipmentRepository shipments;
    private final Clock clock;
    public PackageQueueService(ShipmentRepository shipments, Clock clock) {
        this.shipments = shipments;
        this.clock = clock;
    }
    @Transactional(readOnly = true)
    public List<PackageQueueDtos.PackageDto> list() {
        if (SecurityUtils.currentUser().role() != Role.ADMIN) throw new AccessDeniedException("Admin archive only");
        return shipments.findAll().stream().map(s -> new PackageQueueDtos.PackageDto(s.getId(), s.getTrackingNumber(),
                s.getDestinationCountry(), s.getDestinationCity(), s.getDescription(), s.getPackageType().name(),
                s.getDeclaredWeightKg(), s.getTotalAmount(), s.getServiceFee(), s.getStatus().name(),
                s.getQueueStatus() == null ? "LEGACY" : s.getQueueStatus().name(),
                s.getQueueClaimant() == null ? null : s.getQueueClaimant().getId(),
                s.getQueueClaimant() == null ? null : s.getQueueClaimant().getFirstName() + " " + s.getQueueClaimant().getLastName(),
                s.getQueueExpiresAt(), clock.instant(), s.getCreatedAt(), s.getServicePaymentUploadedAt(), s.allPhotos())).toList();
    }
    private Shipment find(UUID id) {
        return shipments.findById(id).orElseThrow(() -> new NotFoundException("Package not found"));
    }
    @Transactional(readOnly = true)
    public PackageQueueDtos.SenderContactDto senderContact(UUID id) {
        Shipment shipment = find(id);
        ShipmentAccess.assertCanRead(SecurityUtils.currentUser(), shipment);
        var sender = shipment.getSender();
        return new PackageQueueDtos.SenderContactDto(sender.getFirstName() + " " + sender.getLastName(),
                sender.getEmail(), sender.getPhone(), shipment.getPickupAddress());
    }
    @Transactional(readOnly = true)
    public String paymentProof(UUID id) {
        Shipment shipment = find(id);
        var principal = SecurityUtils.currentUser();
        if (principal.role() != Role.ADMIN && (shipment.getQueueClaimant() == null
                || !shipment.getQueueClaimant().getId().equals(principal.userId())))
            throw new AccessDeniedException("Only the original claimant or admin can view this confirmation");
        if (shipment.getServicePaymentProof() == null) throw new NotFoundException("No payment proof uploaded");
        return shipment.getServicePaymentProof();
    }
}

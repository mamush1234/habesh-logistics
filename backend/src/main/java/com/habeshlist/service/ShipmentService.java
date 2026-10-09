package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.*;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.*;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.util.CodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ShipmentService {

    private final ShipmentRepository shipments;
    private final RecipientRepository recipients;
    private final UserRepository users;
    private final AgentProfileRepository agentProfiles;
    private final PickupRepository pickups;
    private final CommissionRepository commissions;
    private final TrackingEventService trackingEventService;
    private final AuthService authService;

    @Value("${app.pricing.default-rate-per-kg}")
    private BigDecimal defaultRate;
    @Value("${app.pricing.default-pickup-fee}")
    private BigDecimal defaultPickupFee;
    @Value("${app.pricing.document-fee:10.00}")
    private BigDecimal documentFee;
    @Value("${app.pricing.medicine-fee:15.00}")
    private BigDecimal medicineFee;

    private static final AtomicInteger TRACKING_SEQ = new AtomicInteger(100);

    public ShipmentService(ShipmentRepository shipments, RecipientRepository recipients,
                           UserRepository users, AgentProfileRepository agentProfiles,
                           PickupRepository pickups, CommissionRepository commissions,
                           TrackingEventService trackingEventService, AuthService authService) {
        this.shipments = shipments;
        this.recipients = recipients;
        this.users = users;
        this.agentProfiles = agentProfiles;
        this.pickups = pickups;
        this.commissions = commissions;
        this.trackingEventService = trackingEventService;
        this.authService = authService;
    }

    /** Initialize tracking sequence based on existing shipments; call at app startup. */
    public synchronized void initTrackingSequence() {
        long count = shipments.count();
        TRACKING_SEQ.set(100 + (int) count);
    }

    private String nextTrackingNumber() {
        return String.format("HL-%06d", TRACKING_SEQ.incrementAndGet());
    }

    @Transactional
    public Shipment createShipment(AuthPrincipal principal, RequestDtos.CreateShipmentRequest req) {
        User actor = users.findById(principal.userId())
                .orElseThrow(() -> new NotFoundException("Sender not found"));
        if (actor.getRole() != Role.SENDER && actor.getRole() != Role.ADMIN) {
            throw new BadRequestException("Only senders can create shipments");
        }
        User sender = actor;
        if (req.senderId() != null && actor.getRole() == Role.ADMIN) {
            sender = users.findById(req.senderId())
                    .orElseThrow(() -> new NotFoundException("Sender not found"));
            if (sender.getRole() != Role.SENDER) throw new BadRequestException("Selected user is not a sender");
        }
        return createFor(sender, req);
    }

    /** Guest checkout: attaches the shipment to a sender account found or created by email. */
    @Transactional
    public Shipment createGuestShipment(RequestDtos.CreateShipmentRequest req) {
        if (isBlank(req.guestFirstName()) || isBlank(req.guestLastName())
                || isBlank(req.guestEmail()) || isBlank(req.guestPhone())) {
            throw new BadRequestException("Name, email and phone are required");
        }
        User sender = users.findByEmailIgnoreCase(req.guestEmail().trim()).orElse(null);
        if (sender == null) {
            sender = authService.createPrivilegedUser(req.guestFirstName().trim(), req.guestLastName().trim(),
                    req.guestEmail().trim(), req.guestPhone().trim(), UUID.randomUUID().toString(), Role.SENDER);
        } else if (sender.getRole() != Role.SENDER) {
            throw new BadRequestException("This email belongs to an existing account. Please log in.");
        }
        // Guests cannot choose their own rate or act on behalf of another sender
        RequestDtos.CreateShipmentRequest safe = new RequestDtos.CreateShipmentRequest(
                req.recipient(), req.destinationCountry(), req.destinationCity(), req.description(),
                req.declaredWeightKg(), null, req.pickupMethod(), req.pickupAddress(),
                req.pickupRequestedDate(), req.pickupTimeWindow(), req.referralCode(),
                null, null, null, null, null, req.packageType(), req.photoDataUrl());
        return createFor(sender, safe);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private Shipment createFor(User sender, RequestDtos.CreateShipmentRequest req) {
        String photo = req.photoDataUrl();
        if (photo != null && photo.isBlank()) photo = null;
        if (photo != null && (!photo.startsWith("data:image/") || photo.length() > 3_000_000)) {
            throw new BadRequestException("Photo must be an image under about 2 MB");
        }
        Recipient r = recipients.save(Recipient.builder()
                .name(req.recipient().name()).phone(req.recipient().phone())
                .city(req.recipient().city()).address(req.recipient().address())
                .notes(req.recipient().notes()).build());

        PackageType type = req.packageType() == null ? PackageType.OTHER : req.packageType();
        BigDecimal rate = req.ratePerKg() == null ? defaultRate : req.ratePerKg();
        BigDecimal shippingFee = switch (type) {
            case DOCUMENT -> documentFee;
            case MEDICINE -> medicineFee;
            case OTHER -> rate.multiply(req.declaredWeightKg());
        };
        shippingFee = shippingFee.setScale(2, RoundingMode.HALF_UP);
        BigDecimal pickupFee = req.pickupMethod() == PickupMethod.PICKUP ? defaultPickupFee : BigDecimal.ZERO;
        BigDecimal total = shippingFee.add(pickupFee);

        User referringAgent = null;
        if (req.referralCode() != null && !req.referralCode().isBlank()) {
            AgentProfile ap = agentProfiles.findByReferralCode(req.referralCode().trim().toUpperCase())
                    .orElseThrow(() -> new BadRequestException("Unknown agent ID / referral code"));
            referringAgent = ap.getUser();
        }

        Shipment s = Shipment.builder()
                .trackingNumber(nextTrackingNumber())
                .trackingToken(CodeGenerator.randomToken(24))
                .sender(sender)
                .recipient(r)
                .destinationCountry(req.destinationCountry())
                .destinationCity(req.destinationCity())
                .description(req.description())
                .declaredWeightKg(req.declaredWeightKg())
                .packageType(type)
                .photoData(photo)
                .ratePerKg(rate)
                .shippingFee(shippingFee)
                .pickupFee(pickupFee)
                .totalAmount(total)
                .paymentStatus(PaymentStatus.PENDING)
                .pickupMethod(req.pickupMethod())
                .pickupAddress(req.pickupAddress())
                .pickupRequestedDate(req.pickupRequestedDate())
                .status(ShipmentStatus.CREATED)
                .referringAgent(referringAgent)
                .assignedAgent(referringAgent)
                .build();
        shipments.save(s);

        trackingEventService.record(s, ShipmentStatus.CREATED, "SHIPMENT_CREATED",
                "Shipment created", "USA", sender);

        if (req.pickupMethod() == PickupMethod.PICKUP) {
            Pickup p = Pickup.builder()
                    .shipment(s)
                    .address(req.pickupAddress() == null ? "TBD" : req.pickupAddress())
                    .date(req.pickupRequestedDate())
                    .timeWindow(req.pickupTimeWindow())
                    .status(PickupStatus.REQUESTED)
                    .build();
            pickups.save(p);
            s.setStatus(ShipmentStatus.PICKUP_SCHEDULED);
            trackingEventService.record(s, ShipmentStatus.PICKUP_SCHEDULED, "PICKUP_REQUESTED",
                    "Pickup requested at " + p.getAddress(), "USA", sender);
        } else {
            s.setStatus(ShipmentStatus.READY_FOR_PICKUP);
            trackingEventService.record(s, ShipmentStatus.READY_FOR_PICKUP, "DROPOFF_SCHEDULED",
                    "Awaiting drop-off at Alexandria", "Alexandria, VA", sender);
        }

        // Create commission record if referred
        if (referringAgent != null) {
            AgentProfile ap = agentProfiles.findById(referringAgent.getId()).orElse(null);
            if (ap != null && ap.getCommissionRate() != null) {
                BigDecimal amt = total.multiply(ap.getCommissionRate()).setScale(2, RoundingMode.HALF_UP);
                commissions.save(Commission.builder()
                        .agent(referringAgent).shipment(s)
                        .amount(amt).rate(ap.getCommissionRate())
                        .status(CommissionStatus.PENDING).build());
            }
        }

        return s;
    }

    @Transactional
    public Shipment updatePayment(UUID shipmentId, PaymentStatus status) {
        Shipment s = getById(shipmentId);
        s.setPaymentStatus(status);
        shipments.save(s);
        return s;
    }

    /** Admin removal, e.g. sender never dropped the package off. Only allowed before it is loaded into luggage. */
    @Transactional
    public void remove(UUID shipmentId) {
        Shipment s = getById(shipmentId);
        if (s.getLuggage() != null) {
            throw new BadRequestException("Shipment is already assigned to luggage; unassign it first");
        }
        shipments.delete(s);
    }

    @Transactional
    public Shipment assignAgent(UUID shipmentId, UUID agentId) {
        Shipment s = getById(shipmentId);
        s.setAssignedAgent(agentId == null ? null : findAgent(agentId));
        return shipments.save(s);
    }

    @Transactional
    public Shipment assignTraveler(UUID shipmentId, UUID travelerId) {
        Shipment s = getById(shipmentId);
        if (travelerId == null) {
            s.setAssignedTraveler(null);
        } else {
            User t = users.findById(travelerId).orElseThrow(() -> new NotFoundException("Traveler not found"));
            if (t.getRole() != Role.TRAVELER) throw new BadRequestException("Selected user is not a traveler");
            s.setAssignedTraveler(t);
        }
        return shipments.save(s);
    }

    public User findAgent(UUID agentId) {
        User a = users.findById(agentId).orElseThrow(() -> new NotFoundException("Agent not found"));
        if (a.getRole() != Role.AGENT) throw new BadRequestException("Selected user is not an agent");
        return a;
    }

    @Transactional(readOnly = true)
    public Shipment getById(UUID id) {
        return shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment not found"));
    }

    @Transactional(readOnly = true)
    public Shipment getByTrackingNumber(String tn) {
        return shipments.findByTrackingNumber(tn).orElseThrow(() -> new NotFoundException("Shipment not found"));
    }

    @Transactional
    public Shipment updateStatus(UUID id, ShipmentStatus newStatus, String location, String description, User performedBy) {
        Shipment s = getById(id);
        s.setStatus(newStatus);
        shipments.save(s);
        trackingEventService.record(s, newStatus, "STATUS_" + newStatus.name(),
                description == null ? ("Status: " + newStatus.name()) : description, location, performedBy);
        return s;
    }

    @Transactional(readOnly = true)
    public List<Shipment> listForUser(AuthPrincipal p) {
        return switch (p.role()) {
            case ADMIN -> shipments.findAll();
            case SENDER -> shipments.findAllBySenderId(p.userId());
            case AGENT -> java.util.stream.Stream.concat(
                            shipments.findAllByReferringAgentId(p.userId()).stream(),
                            shipments.findAllByAssignedAgentId(p.userId()).stream())
                    .distinct().toList();
            case TRAVELER -> java.util.stream.Stream.concat(
                            shipments.findAll().stream()
                                    .filter(s -> s.getLuggage() != null && s.getLuggage().getTrip().getTraveler().getId().equals(p.userId())),
                            shipments.findAllByAssignedTravelerId(p.userId()).stream())
                    .distinct().toList();
        };
    }

    public ShipmentRepository repo() { return shipments; }
}

package com.habeshlist.service;

import com.habeshlist.dto.ApiDtos;
import com.habeshlist.dto.request.RequestDtos;
import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.*;
import com.habeshlist.exception.BadRequestException;
import com.habeshlist.exception.NotFoundException;
import com.habeshlist.repository.*;
import com.habeshlist.security.AuthPrincipal;
import com.habeshlist.security.ShipmentAccess;
import com.habeshlist.util.CodeGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

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
    private final TravelAnnouncementService travelAnnouncements;
    private final PackageNotificationRepository notificationRepository;

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
                           TrackingEventService trackingEventService, TravelAnnouncementService travelAnnouncements,
                           PackageNotificationRepository notificationRepository) {
        this.shipments = shipments;
        this.recipients = recipients;
        this.users = users;
        this.agentProfiles = agentProfiles;
        this.pickups = pickups;
        this.commissions = commissions;
        this.trackingEventService = trackingEventService;
        this.travelAnnouncements = travelAnnouncements;
        this.notificationRepository = notificationRepository;
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

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private Shipment createFor(User sender, RequestDtos.CreateShipmentRequest req) {
        return createFor(sender, req, sender, null);
    }

    @Transactional
    public Shipment createFromGuest(DeliveryRequest source, com.habeshlist.dto.GuestRequestDtos.ConvertInput input) {
        if (com.habeshlist.security.SecurityUtils.currentUser().role() != Role.ADMIN)
            throw new AccessDeniedException("Only admins can convert guest requests");
        if (source.getSender() != null) throw new BadRequestException("Only guest requests can use this conversion");
        User actor = users.findById(com.habeshlist.security.SecurityUtils.currentUser().userId())
                .orElseThrow(() -> new NotFoundException("Administrator not found"));
        if (input.pickupMethod() == PickupMethod.PICKUP
                && (isBlank(input.pickupAddress()) || input.pickupRequestedDate() == null))
            throw new BadRequestException("Pickup requires an address and requested date");
        PackageType type = switch (source.getItemType()) {
            case DOCUMENTS -> PackageType.DOCUMENT;
            case MEDICINE -> PackageType.MEDICINE;
            default -> PackageType.OTHER;
        };
        var req = new RequestDtos.CreateShipmentRequest(input.recipient(), input.destinationCountry().strip(),
                source.getDestinationCity(), source.getDescription(), source.getWeightKg(), null,
                input.pickupMethod(), input.pickupAddress(), input.pickupRequestedDate(), input.pickupTimeWindow(),
                null, null, type, source.getProductPhoto(), null, null, null);
        return createFor(null, req, actor, source);
    }

    private Shipment createFor(User sender, RequestDtos.CreateShipmentRequest req, User actor, DeliveryRequest source) {
        if (req.photoDataUrl() != null && !req.photoDataUrl().isBlank() && req.photoDataUrls() != null) {
            throw new BadRequestException("Provide photoDataUrls or photoDataUrl, not both");
        }
        List<String> photos = req.photoDataUrls() == null
                ? (validatePhoto(req.photoDataUrl()) == null ? List.of() : List.of(req.photoDataUrl()))
                : validatePhotos(req.photoDataUrls());
        Recipient r = saveRecipient(req.recipient());
        List<Recipient> additionalRecipients = req.additionalRecipients() == null ? List.of()
                : req.additionalRecipients().stream().map(this::saveRecipient).toList();

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
                .sourceRequest(source)
                .recipient(r)
                .destinationCountry(req.destinationCountry())
                .destinationCity(req.destinationCity())
                .description(req.description())
                .declaredWeightKg(req.declaredWeightKg())
                .packageType(type)
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
                .build();
        s.getAdditionalRecipients().addAll(additionalRecipients);
        s.replacePhotos(photos);
        travelAnnouncements.requestSpace(s, req.travelAnnouncementId());
        shipments.save(s);

        trackingEventService.record(s, ShipmentStatus.CREATED, "SHIPMENT_CREATED",
                source == null ? "Shipment created" : "Shipment created from guest delivery request " + source.getId(), "USA", actor);

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
                    "Pickup requested at " + p.getAddress(), "USA", actor);
        } else {
            s.setStatus(ShipmentStatus.READY_FOR_PICKUP);
            trackingEventService.record(s, ShipmentStatus.READY_FOR_PICKUP, "DROPOFF_SCHEDULED",
                    "Awaiting drop-off at Alexandria", "Alexandria, VA", actor);
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
    public Shipment updatePhoto(UUID shipmentId, String photoDataUrl, AuthPrincipal principal) {
        Shipment s = editablePhotos(shipmentId, principal);
        String photo = validatePhoto(photoDataUrl);
        if (photo == null) throw new BadRequestException("A package picture is required");
        s.setPhotoData(photo);
        return shipments.save(s);
    }

    @Transactional
    public Shipment updatePhotos(UUID shipmentId, List<String> photoDataUrls, AuthPrincipal principal) {
        Shipment s = editablePhotos(shipmentId, principal);
        s.replacePhotos(validatePhotos(photoDataUrls));
        return shipments.save(s);
    }

    private Shipment editablePhotos(UUID shipmentId, AuthPrincipal principal) {
        Shipment s = shipments.findForUpdate(shipmentId).orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (principal.role() != Role.ADMIN && !(principal.role() == Role.SENDER
                && s.getSender() != null && s.getSender().getId().equals(principal.userId()))) {
            throw new AccessDeniedException("Only an admin or the package's sender can update its picture");
        }
        return s;
    }

    private Recipient saveRecipient(RequestDtos.RecipientRequest req) {
        if (req == null || isBlank(req.name()) || isBlank(req.phone()) || isBlank(req.city())) {
            throw new BadRequestException("Each recipient requires a name, phone and city");
        }
        return recipients.save(Recipient.builder().name(req.name()).phone(req.phone())
                .city(req.city()).address(req.address()).notes(req.notes()).build());
    }

    private static List<String> validatePhotos(List<String> photos) {
        if (photos == null || photos.size() > 5) {
            throw new BadRequestException("A package can have up to 5 pictures");
        }
        for (String photo : photos) {
            if (validatePhoto(photo) == null) throw new BadRequestException("Each picture must be a nonempty image");
        }
        return photos;
    }

    private static String validatePhoto(String photo) {
        if (photo == null || photo.isBlank()) return null;
        if (!photo.startsWith("data:image/") || photo.length() > 3_000_000) {
            throw new BadRequestException("Photo must be an image under about 2 MB");
        }
        return photo;
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
        if (s.getSourceRequest() != null) {
            throw new BadRequestException("Converted guest shipments must be cancelled, not deleted, to preserve the request and tracking history");
        }
        if (s.getLuggage() != null || s.getTrip() != null) {
            throw new BadRequestException("Shipment is already assigned to luggage or a trip; unassign it first");
        }
        notificationRepository.deleteAllByShipmentId(shipmentId);
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
        Shipment s = shipments.findForUpdate(id).orElseThrow(() -> new NotFoundException("Shipment not found"));
        s.setStatus(newStatus);
        if (newStatus == ShipmentStatus.CANCELLED && s.getTravelAnnouncement() != null) {
            s.setTravelBookingStatus(TravelBookingStatus.CANCELLED);
        }
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
            case AGENT -> shipments.findAll().stream()
                    .filter(s -> ShipmentAccess.canRead(p, s)).toList();
            case TRAVELER -> java.util.stream.Stream.concat(
                            shipments.findAll().stream()
                                    .filter(s -> ShipmentAccess.canRead(p, s)),
                            shipments.findAllByAssignedTravelerId(p.userId()).stream()
                                    .filter(s -> ShipmentAccess.canRead(p, s)))
                    .distinct().toList();
        };
    }

    public ShipmentRepository repo() { return shipments; }
}

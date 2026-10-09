package com.habeshlist.service;

import com.habeshlist.entity.*;
import com.habeshlist.entity.enums.*;
import com.habeshlist.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

@Component
public class DataSeeder {

    private final AuthService authService;
    private final UserRepository users;
    private final ShipmentService shipmentService;
    private final TripService tripService;
    private final ShipmentRepository shipments;
    private final TripRepository trips;
    private final LuggageRepository luggageRepo;
    private final LuggageService luggageService;
    private final RecipientRepository recipients;
    private final AgentProfileRepository agentProfiles;
    private final CommissionRepository commissionRepo;
    private final TrackingEventService trackingEventService;

    @Value("${app.pricing.default-rate-per-kg}")
    private BigDecimal defaultRate;
    @Value("${app.pricing.default-commission-rate}")
    private BigDecimal defaultCommission;

    public DataSeeder(AuthService authService, UserRepository users, ShipmentService shipmentService,
                      TripService tripService, ShipmentRepository shipments, TripRepository trips,
                      LuggageRepository luggageRepo, LuggageService luggageService,
                      RecipientRepository recipients, AgentProfileRepository agentProfiles,
                      CommissionRepository commissionRepo,
                      TrackingEventService trackingEventService) {
        this.authService = authService;
        this.users = users;
        this.shipmentService = shipmentService;
        this.tripService = tripService;
        this.shipments = shipments;
        this.trips = trips;
        this.luggageRepo = luggageRepo;
        this.luggageService = luggageService;
        this.recipients = recipients;
        this.agentProfiles = agentProfiles;
        this.commissionRepo = commissionRepo;
        this.trackingEventService = trackingEventService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        shipmentService.initTrackingSequence();
        tripService.initSequences();

        if (users.count() > 0) return;

        User admin = authService.createPrivilegedUser("Admin", "User", "admin@habeshlist.com",
                "+1-555-0100", "Admin@123", Role.ADMIN);
        User traveler = authService.createPrivilegedUser("Ahmed", "Hassan", "traveler@example.com",
                "+1-555-0101", "Traveler@123", Role.TRAVELER);
        User sender1 = authService.createPrivilegedUser("Meron", "Tadesse", "sender@example.com",
                "+1-555-0102", "Sender@123", Role.SENDER);
        User sender2 = authService.createPrivilegedUser("Yonas", "Bekele", "sender2@example.com",
                "+1-555-0103", "Sender@123", Role.SENDER);
        User agent = authService.createPrivilegedUser("Beti", "Alemu", "agent@example.com",
                "+1-555-0104", "Agent@123", Role.AGENT);

        // Set agent commission rate and referral code
        AgentProfile ap = agentProfiles.findById(agent.getId()).orElseThrow();
        ap.setCommissionRate(defaultCommission);
        ap.setReferralCode("BETI2026");
        ap.setCountry("USA"); ap.setCity("Alexandria");
        agentProfiles.save(ap);

        // Create a trip and luggage manually to match spec example (TRIP-2026-0045 / LUG-2026-0001)
        Trip trip = Trip.builder()
                .tripNumber("TRIP-" + Year.now().getValue() + "-0045")
                .traveler(traveler)
                .departureAirport("IAD").arrivalAirport("ADD")
                .departureDate(LocalDate.now().plusDays(20))
                .expectedArrivalDate(LocalDate.now().plusDays(21))
                .totalLuggageCapacity(new BigDecimal("46.00"))
                .numberOfLuggage(2)
                .status(TripStatus.FILLING)
                .notes("Demo trip")
                .build();
        trips.save(trip);

        Luggage lug1 = luggageRepo.save(Luggage.builder()
                .luggageNumber("LUG-" + Year.now().getValue() + "-0001")
                .trip(trip).capacityKg(new BigDecimal("23.00"))
                .actualWeightKg(BigDecimal.ZERO).status(LuggageStatus.PACKING).build());
        Luggage lug2 = luggageRepo.save(Luggage.builder()
                .luggageNumber("LUG-" + Year.now().getValue() + "-0002")
                .trip(trip).capacityKg(new BigDecimal("23.00"))
                .actualWeightKg(BigDecimal.ZERO).status(LuggageStatus.PACKING).build());

        // Create 5 recipients + shipments HL-000101..HL-000105
        BigDecimal[] weights = {new BigDecimal("5"), new BigDecimal("8"), new BigDecimal("4"),
                new BigDecimal("6"), new BigDecimal("7")};
        String[] names = {"Abebe K.", "Tigist M.", "Dawit S.", "Hanna G.", "Samuel A."};
        String[] cities = {"Addis Ababa", "Addis Ababa", "Bahir Dar", "Addis Ababa", "Hawassa"};

        for (int i = 0; i < 5; i++) {
            Recipient r = recipients.save(Recipient.builder()
                    .name(names[i]).phone("+251-911-" + (100000 + i))
                    .city(cities[i]).address("Area " + (i + 1)).build());
            User sender = i < 3 ? sender1 : sender2;
            BigDecimal shippingFee = weights[i].multiply(defaultRate);
            Shipment s = Shipment.builder()
                    .trackingNumber(String.format("HL-%06d", 101 + i))
                    .trackingToken(com.habeshlist.util.CodeGenerator.randomToken(24))
                    .sender(sender).recipient(r)
                    .destinationCountry("Ethiopia").destinationCity(cities[i])
                    .description("Clothing and gifts")
                    .declaredWeightKg(weights[i])
                    .ratePerKg(defaultRate)
                    .shippingFee(shippingFee)
                    .pickupFee(BigDecimal.ZERO)
                    .totalAmount(shippingFee)
                    .paymentStatus(PaymentStatus.PAID)
                    .pickupMethod(PickupMethod.ALEXANDRIA_DROPOFF)
                    .status(ShipmentStatus.RECEIVED_AT_ALEXANDRIA)
                    .referringAgent(i == 0 ? agent : null)
                    .build();
            shipments.save(s);
            if (i == 0) {
                // Create commission record for the referred shipment HL-000101
                commissionRepo.save(Commission.builder()
                        .agent(agent).shipment(s)
                        .amount(shippingFee.multiply(defaultCommission).setScale(2, java.math.RoundingMode.HALF_UP))
                        .rate(defaultCommission)
                        .status(CommissionStatus.PENDING)
                        .build());
            }
            trackingEventService.record(s, ShipmentStatus.CREATED, "SHIPMENT_CREATED", "Shipment created", "USA", sender);
            trackingEventService.record(s, ShipmentStatus.RECEIVED_AT_ALEXANDRIA, "RECEIVED_AT_ALEXANDRIA",
                    "Received at Alexandria drop-off", "Alexandria, VA", admin);
        }

        // Assign shipments to luggage per spec example
        // LUG-01: HL-000101 (5), HL-000102 (8), HL-000103 (4) = 17 kg
        // LUG-02: HL-000104 (6), HL-000105 (7) = 13 kg
        List<Shipment> all = shipments.findAll();
        for (Shipment s : all) {
            if (s.getTrackingNumber().equals("HL-000101")
                    || s.getTrackingNumber().equals("HL-000102")
                    || s.getTrackingNumber().equals("HL-000103")) {
                luggageService.assignShipment(lug1.getId(), s.getId(), admin);
            } else if (s.getTrackingNumber().equals("HL-000104")
                    || s.getTrackingNumber().equals("HL-000105")) {
                luggageService.assignShipment(lug2.getId(), s.getId(), admin);
            }
        }

        // Re-init sequences based on real counts
        shipmentService.initTrackingSequence();
        tripService.initSequences();
    }
}

package com.habeshlist.entity.enums;

public final class MarketplaceTypes {
    private MarketplaceTypes() {}

    public enum DeliveryStatus {
        DRAFT, REQUESTED, MATCHED, PAID_ESCROW, PICKED_UP, IN_TRANSIT, ARRIVED, DELIVERED, COMPLETED, CANCELLED, DISPUTED
    }
    public enum ItemType { DOCUMENTS, CLOTHING, ELECTRONICS, MEDICINE, FOOD, OTHER, SHOES, LAPTOP, COSMETICS }
    public enum OfferStatus { PENDING, ACCEPTED, DECLINED, WITHDRAWN }
    public enum EscrowStatus { NONE, DEMO_HELD, DEMO_RELEASED, DEMO_REFUNDED, STRIPE_TEST_HELD, STRIPE_TEST_RELEASED, STRIPE_TEST_REFUNDED }
    public enum VerificationLevel { UNVERIFIED, ID_VERIFIED, FULLY_VERIFIED }
}

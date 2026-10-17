package com.habeshlist.entity.enums;

public final class MarketplaceTypes {
    private MarketplaceTypes() {}

    public enum DeliveryStatus {
        REQUESTED, MATCHED, PAID_ESCROW, PICKED_UP, IN_TRANSIT, DELIVERED, COMPLETED, CANCELLED, DISPUTED
    }
    public enum ItemType { DOCUMENTS, CLOTHING, ELECTRONICS, MEDICINE, FOOD, OTHER }
    public enum OfferStatus { PENDING, ACCEPTED, DECLINED, WITHDRAWN }
    public enum EscrowStatus { NONE, DEMO_HELD, DEMO_RELEASED, DEMO_REFUNDED }
    public enum VerificationLevel { UNVERIFIED, ID_VERIFIED, FULLY_VERIFIED }
}

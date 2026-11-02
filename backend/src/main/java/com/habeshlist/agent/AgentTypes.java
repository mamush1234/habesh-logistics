package com.habeshlist.agent;

public final class AgentTypes {
    private AgentTypes() {}

    public enum CustomerType { SENDER, TRAVELER }
    public enum AuthorizationStatus { ACTIVE, REVOKED }
    public enum AuthorizationMethod { IN_PERSON, PHONE, WRITTEN, DIGITAL }
}

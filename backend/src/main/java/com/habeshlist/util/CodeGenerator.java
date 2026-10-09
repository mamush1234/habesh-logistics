package com.habeshlist.util;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class CodeGenerator {
    private static final SecureRandom RNG = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private CodeGenerator() {}

    public static String randomToken(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    public static String referralCode(String firstName, int year) {
        String fn = firstName == null ? "AGT" : firstName.toUpperCase().replaceAll("[^A-Z]", "");
        if (fn.length() > 5) fn = fn.substring(0, 5);
        if (fn.isEmpty()) fn = "AGT";
        return fn + year + randomToken(2);
    }
}

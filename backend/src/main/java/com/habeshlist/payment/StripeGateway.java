package com.habeshlist.payment;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StripeGateway {
    private final String secret;
    private final String publishable;
    private final String webhookSecret;

    public StripeGateway(@Value("${app.stripe.test-secret-key:}") String secret,
            @Value("${app.stripe.test-publishable-key:}") String publishable,
            @Value("${app.stripe.test-webhook-secret:}") String webhookSecret) {
        this.secret = secret;
        this.publishable = publishable;
        this.webhookSecret = webhookSecret;
    }

    public boolean configured() {
        return secret.startsWith("sk_test_") && publishable.startsWith("pk_test_")
                && webhookSecret.startsWith("whsec_");
    }

    public String publishableKey() {
        requireConfigured();
        return publishable;
    }

    public String webhookSecret() {
        requireConfigured();
        return webhookSecret;
    }

    private void requireConfigured() {
        if (!configured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Stripe test payments are not configured. Live keys are not supported.");
    }

    private RequestOptions options(String key) {
        requireConfigured();
        return RequestOptions.builder().setApiKey(secret).setIdempotencyKey(key)
                .setConnectTimeout(5000).setReadTimeout(15000).setMaxNetworkRetries(2).build();
    }

    public PaymentIntent create(StripePayment p) throws StripeException {
        return PaymentIntent.create(PaymentIntentCreateParams.builder()
                .setAmount(p.getAmountCents()).setCurrency("usd")
                .addAllowedPaymentMethodType(PaymentIntentCreateParams.AllowedPaymentMethodType.CARD)
                .putMetadata("habesh_payment_id", p.getId().toString())
                .putMetadata("package_id", p.getPackageRequest().getId().toString())
                .build(), options("habesh-test-payment-" + p.getId()));
    }

    public PaymentIntent retrieve(String id) throws StripeException {
        return PaymentIntent.retrieve(id, options(null));
    }

    public PaymentIntent cancel(String id, String key) throws StripeException {
        return retrieve(id).cancel(options(key));
    }

    public Refund refund(String id, String key) throws StripeException {
        Refund refund = Refund.create(RefundCreateParams.builder().setPaymentIntent(id).build(), options(key));
        return Refund.retrieve(refund.getId(), options(null));
    }
}

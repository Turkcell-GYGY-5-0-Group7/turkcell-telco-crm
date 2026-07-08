package com.telco.platform.starter.security;

import com.telco.platform.common.context.CorrelationConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for platform security under {@code telco.platform.security}.
 *
 * <p>Supports two trust models per ADR-011: validating an {@code Authorization: Bearer} JWT, or
 * trusting user identity headers injected by a fronting API gateway.</p>
 */
@ConfigurationProperties(prefix = "telco.platform.security")
public class JwtProperties {

    /** Master switch for the security auto-configuration. Defaults to enabled. */
    private boolean enabled = true;

    /** JWT validation/issuance settings. */
    private Jwt jwt = new Jwt();

    /** Gateway-behind-trust settings (ADR-011 internal services). */
    private GatewayTrust gatewayTrust = new GatewayTrust();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public GatewayTrust getGatewayTrust() {
        return gatewayTrust;
    }

    public void setGatewayTrust(GatewayTrust gatewayTrust) {
        this.gatewayTrust = gatewayTrust;
    }

    /** JWT signing/verification configuration. */
    public static class Jwt {

        /** Base64-encoded HMAC secret. Used when {@link #publicKey} is not set. */
        private String secret;

        /** PEM-encoded RSA public key for verification (alternative to {@link #secret}). */
        private String publicKey;

        /** Expected token issuer; when set, tokens are validated against it. */
        private String issuer = "telco";

        /** Token lifetime in seconds, used by the optional issue helper. */
        private long expirySeconds = 3600;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public long getExpirySeconds() {
            return expirySeconds;
        }

        public void setExpirySeconds(long expirySeconds) {
            this.expirySeconds = expirySeconds;
        }
    }

    /** Trust model that reads user identity from gateway-injected headers. */
    public static class GatewayTrust {

        /** When true, the filter trusts the configured user headers instead of validating a JWT. */
        private boolean enabled = false;

        /** Header carrying the user id. Defaults to {@link CorrelationConstants#HEADER_USER_ID}. */
        private String userIdHeader = CorrelationConstants.HEADER_USER_ID;

        /** Header carrying comma-separated roles. Defaults to {@link CorrelationConstants#HEADER_USER_ROLES}. */
        private String rolesHeader = CorrelationConstants.HEADER_USER_ROLES;

        /**
         * Header carrying the resolved customer-service {@code customerId} (identity-to-customer
         * linkage, ADR-011). Defaults to {@link CorrelationConstants#HEADER_CUSTOMER_ID}.
         */
        private String customerIdHeader = CorrelationConstants.HEADER_CUSTOMER_ID;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUserIdHeader() {
            return userIdHeader;
        }

        public void setUserIdHeader(String userIdHeader) {
            this.userIdHeader = userIdHeader;
        }

        public String getRolesHeader() {
            return rolesHeader;
        }

        public void setRolesHeader(String rolesHeader) {
            this.rolesHeader = rolesHeader;
        }

        public String getCustomerIdHeader() {
            return customerIdHeader;
        }

        public void setCustomerIdHeader(String customerIdHeader) {
            this.customerIdHeader = customerIdHeader;
        }
    }
}

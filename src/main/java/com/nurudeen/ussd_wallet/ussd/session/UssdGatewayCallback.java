package com.nurudeen.ussd_wallet.ussd.session;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ussd_gateway_callbacks")
public class UssdGatewayCallback {

    @EmbeddedId
    private UssdGatewayCallbackId id;

    @Column(name = "response_type", nullable = false, length = 10)
    private String responseType;

    @Column(name = "response_text", length = 182)
    private String responseText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UssdGatewayCallback() {
    }

    public UssdGatewayCallback(String sessionId, String callbackId, String responseType, String responseText) {
        this.id = new UssdGatewayCallbackId(sessionId, callbackId);
        this.responseType = responseType;
        this.responseText = responseText;
    }

    public String getResponseType() {
        return responseType;
    }

    public String getResponseText() {
        return responseText;
    }

    @jakarta.persistence.Embeddable
    public static class UssdGatewayCallbackId implements Serializable {

        @Column(name = "session_id", nullable = false, length = 128)
        private String sessionId;

        @Column(name = "callback_id", nullable = false, length = 128)
        private String callbackId;

        protected UssdGatewayCallbackId() {
        }

        public UssdGatewayCallbackId(String sessionId, String callbackId) {
            this.sessionId = sessionId;
            this.callbackId = callbackId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof UssdGatewayCallbackId that)) {
                return false;
            }
            return Objects.equals(sessionId, that.sessionId)
                    && Objects.equals(callbackId, that.callbackId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId, callbackId);
        }
    }
}

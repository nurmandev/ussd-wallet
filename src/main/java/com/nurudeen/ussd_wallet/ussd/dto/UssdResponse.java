package com.nurudeen.ussd_wallet.ussd.dto;

public record UssdResponse(Type type, String message) {

    public enum Type {
        CONTINUE,
        END
    }

    public String toGatewayText() {
        return (type == Type.CONTINUE ? "CON " : "END ") + message;
    }
}

package com.nurudeen.ussd_wallet.ussd.controller;

import com.nurudeen.ussd_wallet.ussd.dto.UssdGatewayRequest;
import com.nurudeen.ussd_wallet.ussd.service.UssdGatewayAuthenticationService;
import com.nurudeen.ussd_wallet.ussd.service.UssdGatewayAdapter;
import com.nurudeen.ussd_wallet.ussd.service.UssdSessionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ussd")
public class UssdGatewayController {

    private final UssdSessionService sessionService;
    private final UssdGatewayAuthenticationService authenticationService;
    private final UssdGatewayAdapter gatewayAdapter;

    public UssdGatewayController(
            UssdSessionService sessionService,
            UssdGatewayAuthenticationService authenticationService,
            UssdGatewayAdapter gatewayAdapter) {
        this.sessionService = sessionService;
        this.authenticationService = authenticationService;
        this.gatewayAdapter = gatewayAdapter;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handle(
            @Valid @RequestBody UssdGatewayRequest request,
            @RequestHeader("X-USSD-Timestamp") String timestamp,
            @RequestHeader("X-USSD-Signature") String signature) {
        authenticationService.verify(request, timestamp, signature);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(gatewayAdapter.render(sessionService.process(request)));
    }
}

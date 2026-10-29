package com.nurudeen.ussd_wallet.ussd.controller;

import com.nurudeen.ussd_wallet.common.exception.CustomerIdentityUnavailableException;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.repository.CustomerRepository;
import com.nurudeen.ussd_wallet.customer.service.PhoneNumberNormalizer;
import com.nurudeen.ussd_wallet.ussd.config.UssdProperties;
import com.nurudeen.ussd_wallet.ussd.dto.UssdGatewayRequest;
import com.nurudeen.ussd_wallet.ussd.dto.UssdSimulatorInputRequest;
import com.nurudeen.ussd_wallet.ussd.dto.UssdSimulatorResponse;
import com.nurudeen.ussd_wallet.ussd.dto.UssdSimulatorStartRequest;
import com.nurudeen.ussd_wallet.ussd.service.UssdSessionService;
import com.nurudeen.ussd_wallet.ussd.service.UssdSimulatorAuthenticationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ussd/simulator")
@Profile("!prod")
@ConditionalOnProperty(name = "ussd.simulator-enabled", havingValue = "true")
public class UssdSimulatorController {

    private final CustomerRepository customerRepository;
    private final UssdSessionService sessionService;
    private final UssdProperties properties;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final UssdSimulatorAuthenticationService authenticationService;

    public UssdSimulatorController(
            CustomerRepository customerRepository,
            UssdSessionService sessionService,
            UssdProperties properties,
            PhoneNumberNormalizer phoneNumberNormalizer,
            UssdSimulatorAuthenticationService authenticationService) {
        this.customerRepository = customerRepository;
        this.sessionService = sessionService;
        this.properties = properties;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/start")
    public ResponseEntity<UssdSimulatorResponse> start(
            @org.springframework.web.bind.annotation.RequestHeader("X-USSD-Simulator-Secret")
                    String simulatorSecret,
            @Valid @RequestBody UssdSimulatorStartRequest request) {
        authenticationService.verify(simulatorSecret);
        Customer customer = customerRepository.findByPhoneNumber(
                        phoneNumberNormalizer.normalize(request.phoneNumber()))
                .orElseThrow(CustomerIdentityUnavailableException::new);
        UUID customerId = customer.getId();
        String sessionId = UUID.randomUUID().toString();
        UssdGatewayRequest gatewayRequest = new UssdGatewayRequest(
                sessionId,
                request.serviceCode(),
                customer.getPhoneNumber(),
                "",
                "LOCAL",
                "start-" + UUID.randomUUID());
        var response = sessionService.processSimulator(gatewayRequest, customerId);
        return ResponseEntity.ok(new UssdSimulatorResponse(sessionId, response.toGatewayText()));
    }

    @PostMapping("/input")
    public ResponseEntity<UssdSimulatorResponse> input(
            @org.springframework.web.bind.annotation.RequestHeader("X-USSD-Simulator-Secret")
                    String simulatorSecret,
            @Valid @RequestBody UssdSimulatorInputRequest request) {
        authenticationService.verify(simulatorSecret);
        Customer customer = customerRepository.findByPhoneNumber(
                        phoneNumberNormalizer.normalize(request.phoneNumber()))
                .orElseThrow(CustomerIdentityUnavailableException::new);
        UUID customerId = customer.getId();
        UssdGatewayRequest gatewayRequest = new UssdGatewayRequest(
                request.sessionId(),
                properties.serviceCode(),
                customer.getPhoneNumber(),
                request.text(),
                "LOCAL",
                request.callbackId());
        var response = sessionService.processSimulator(gatewayRequest, customerId);
        return ResponseEntity.ok(new UssdSimulatorResponse(
                request.sessionId(), response.toGatewayText()));
    }

    @PostMapping("/end")
    public ResponseEntity<UssdSimulatorResponse> end(
            @org.springframework.web.bind.annotation.RequestHeader("X-USSD-Simulator-Secret")
                    String simulatorSecret,
            @Valid @RequestBody UssdSimulatorInputRequest request) {
        authenticationService.verify(simulatorSecret);
        Customer customer = customerRepository.findByPhoneNumber(
                        phoneNumberNormalizer.normalize(request.phoneNumber()))
                .orElseThrow(CustomerIdentityUnavailableException::new);
        UUID customerId = customer.getId();
        UssdGatewayRequest gatewayRequest = new UssdGatewayRequest(
                request.sessionId(),
                properties.serviceCode(),
                customer.getPhoneNumber(),
                "CANCEL",
                "LOCAL",
                request.callbackId());
        var response = sessionService.processSimulator(gatewayRequest, customerId);
        return ResponseEntity.ok(new UssdSimulatorResponse(
                request.sessionId(), response.toGatewayText()));
    }

}

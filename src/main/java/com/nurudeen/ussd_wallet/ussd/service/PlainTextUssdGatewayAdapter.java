package com.nurudeen.ussd_wallet.ussd.service;

import com.nurudeen.ussd_wallet.ussd.dto.UssdResponse;
import org.springframework.stereotype.Component;

@Component
public class PlainTextUssdGatewayAdapter implements UssdGatewayAdapter {

    @Override
    public String render(UssdResponse response) {
        return response.toGatewayText();
    }
}

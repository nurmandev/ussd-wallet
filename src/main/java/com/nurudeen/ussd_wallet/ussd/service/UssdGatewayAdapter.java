package com.nurudeen.ussd_wallet.ussd.service;

import com.nurudeen.ussd_wallet.ussd.dto.UssdResponse;

public interface UssdGatewayAdapter {

    String render(UssdResponse response);
}

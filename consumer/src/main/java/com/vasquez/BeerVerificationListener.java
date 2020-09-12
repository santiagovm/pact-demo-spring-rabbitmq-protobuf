package com.vasquez;

import com.google.protobuf.ByteString;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.stereotype.Component;

@Slf4j
@AllArgsConstructor
@Component
public class BeerVerificationListener {

    private final BeerVerificationService beerVerificationService;

    @StreamListener(Sink.INPUT)
    public void listen(byte[] rawMessage) {
        Response responseProto = parseResponseProtoBytes(rawMessage);
        BeerVerification verification = new BeerVerification(responseProto);
        beerVerificationService.process(verification);
    }

    @SneakyThrows
    private Response parseResponseProtoBytes(byte[] envelopeProtoBytes) {

        SomeCustomEnvelope messageEnvelope = SomeCustomEnvelope.parseFrom(envelopeProtoBytes);
        ByteString eventDataString = messageEnvelope.getEventData();
        byte[] eventDataBytes = eventDataString.toByteArray();
        return Response.parseFrom(eventDataBytes);
    }
}

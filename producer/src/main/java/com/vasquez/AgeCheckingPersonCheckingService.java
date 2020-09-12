package com.vasquez;

import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AgeCheckingPersonCheckingService implements PersonCheckingService {

    private final MessagePublisher messagePublisher;

    public AgeCheckingPersonCheckingService(MessagePublisher messagePublisher) {
        this.messagePublisher = messagePublisher;
    }

    @Override
    public void shouldGetBeer(int age) {

        Response.Builder responseBuilder = Response.newBuilder();

        if (age < 21) {
            responseBuilder
                    .setName("sebastian vasquez")
                    .setStatus(Response.BeerCheckStatus.NOT_OK)
                    .setBeersCount(0)
                    .setCity("new york")
                    .setDob(Instant.parse("2011-02-15T15:15:15Z").getEpochSecond());
        } else {
            responseBuilder
                    .setName("santiago vasquez")
                    .setStatus(Response.BeerCheckStatus.OK)
                    .setBeersCount(7)
                    .setCity("medellin")
                    .setDob(Instant.parse("1975-04-01T12:00:00Z").getEpochSecond());
        }

        Response responseProto = responseBuilder.build();

        byte[] responseProtoBytes = responseProto.toByteArray();
        ByteString responseProtoByteString = ByteString.copyFrom(responseProtoBytes);

        SomeCustomEnvelope messageEnvelope = SomeCustomEnvelope.newBuilder()
                .setMessageType("foo-message-type")
                .setEventData(responseProtoByteString)
                .build();

        byte[] messageEnvelopeBytes = messageEnvelope.toByteArray();

        messagePublisher.publishMessage(messageEnvelopeBytes);
    }
}

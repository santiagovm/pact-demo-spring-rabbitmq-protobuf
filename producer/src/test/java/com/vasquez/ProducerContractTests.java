package com.vasquez;

import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit.PactRunner;
import au.com.dius.pact.provider.junit.target.MessageTarget;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.junitsupport.target.Target;
import au.com.dius.pact.provider.junitsupport.target.TestTarget;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(PactRunner.class)
@Provider("pact-beer-api-producer")
@PactFolder("pacts")
public class ProducerContractTests {

    private final MessagePublisher messagePublisher = Mockito.mock(MessagePublisher.class);

    private final PersonCheckingService personCheckingService = new AgeCheckingPersonCheckingService(messagePublisher);

    private int age;

    @PactVerifyProvider("an accepted verification message")
    public String publish_an_accepted_verification_message() {

        // given
        doNothing()
                .when(messagePublisher)
                .publishMessage(any(byte[].class));

        // when
        personCheckingService.shouldGetBeer(age);

        // then
        ArgumentCaptor<byte[]> publishMessageArgumentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(messagePublisher, times(1)).publishMessage(publishMessageArgumentCaptor.capture());

        byte[] messageEnvelopeProtoBytes = publishMessageArgumentCaptor.getValue();
        return createPactMessageContents(messageEnvelopeProtoBytes);
    }

    @PactVerifyProvider("a rejected verification message")
    public String publish_a_rejected_verification_message() {

        // given
        doNothing()
                .when(messagePublisher)
                .publishMessage(any(byte[].class));

        // when
        personCheckingService.shouldGetBeer(age);

        // then
        ArgumentCaptor<byte[]> publishMessageArgumentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(messagePublisher, times(1)).publishMessage(publishMessageArgumentCaptor.capture());

        byte[] messageEnvelopeProtoBytes = publishMessageArgumentCaptor.getValue();
        return createPactMessageContents(messageEnvelopeProtoBytes);
    }

    @State("the patron is 45 years old")
    public void the_patron_is_45_years_old() {
        age = 45;
    }

    @State("the patron is 9 years old")
    public void the_patron_is_9_years_old() {
        age = 9;
    }

    private String createPactMessageContents(byte[] messageEnvelopeProtoBytes) {

        byte[] appMessageEnvelopeProtoBytesBase64Bytes = Base64.getEncoder().encode(messageEnvelopeProtoBytes);
        String appMessageEnvelopeProtoBytesBase64String = new String(appMessageEnvelopeProtoBytesBase64Bytes);

        return "{\"proto-byte-string\": \"" + appMessageEnvelopeProtoBytesBase64String + "\"}";
    }

    @TestTarget
    public final Target target = new MessageTarget();
}

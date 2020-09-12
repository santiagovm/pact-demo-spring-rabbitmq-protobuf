package com.vasquez;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.google.protobuf.ByteString;
import lombok.SneakyThrows;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Sink;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ExtendWith(PactConsumerTestExt.class)
class ConsumerContractTests {

    @Autowired
    BeerVerificationServiceMock beerVerificationServiceMock;

    @Autowired
    Sink sink;

    @BeforeEach
    public void beforeEach() {
        beerVerificationServiceMock.reset();
    }

    @Pact(consumer = "pact-beer-api-consumer")
    public MessagePact createAcceptedVerificationPact(MessagePactBuilder messagePactBuilder) {

        Response responseProto = Response.newBuilder()
                .setName("santiago vasquez")
                .setStatus(Response.BeerCheckStatus.OK)
                .setBeersCount(7)
                .setCity("medellin")
                .setDob(Instant.parse("1975-04-01T12:00:00Z").getEpochSecond())
                .build();

        byte[] responseProtoBytes = responseProto.toByteArray();
        ByteString responseProtoByteString = ByteString.copyFrom(responseProtoBytes);

        SomeCustomEnvelope appMessageEnvelopeProto = SomeCustomEnvelope.newBuilder()
                .setMessageType("foo-message-type")
                .setEventData(responseProtoByteString)
                .build();

        DslPart pactMessageContent = createPactDslPart(appMessageEnvelopeProto);

        return messagePactBuilder
                .hasPactWith("pact-beer-api-producer")
                .given("the patron is 45 years old")
                .expectsToReceive("an accepted verification message")
                .withMetadata(Map.of("content-type", "some-mime-type"))
                .withContent(pactMessageContent)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createAcceptedVerificationPact", providerType = ProviderType.ASYNCH)
    void should_call_beer_verification_service_when_verification_was_accepted(MessagePact verificationAcceptedPact) {

        // when
        publishMessageToQueue(verificationAcceptedPact);

        // assert
        BeerVerification verification = beerVerificationServiceMock.getVerificationInRequest();

        assertThat(verification, is(notNullValue()));

        assertThat(verification.getName(), is(equalTo("santiago vasquez")));
        assertThat(verification.getIsApproved(), is(equalTo(true)));
        assertThat(verification.getBeersCount(), is(equalTo(7)));
        assertThat(verification.getCity(), is(equalTo("medellin")));
        assertThat(verification.getDateOfBirth(), is(equalTo(Instant.parse("1975-04-01T12:00:00Z"))));
    }

    @Pact(consumer = "pact-beer-api-consumer")
    public MessagePact createRejectedVerificationPact(MessagePactBuilder messagePactBuilder) {

        Response responseProto = Response.newBuilder()
                .setName("sebastian vasquez")
                .setStatus(Response.BeerCheckStatus.NOT_OK)
                .setBeersCount(0)
                .setCity("new york")
                .setDob(Instant.parse("2011-02-15T15:15:15Z").getEpochSecond())
                .build();

        byte[] responseProtoBytes = responseProto.toByteArray();
        ByteString responseProtoByteString = ByteString.copyFrom(responseProtoBytes);

        SomeCustomEnvelope appMessageEnvelopeProto = SomeCustomEnvelope.newBuilder()
                .setMessageType("foo-message-type")
                .setEventData(responseProtoByteString)
                .build();

        DslPart pactMessageContent = createPactDslPart(appMessageEnvelopeProto);

        return messagePactBuilder
                .hasPactWith("pact-beer-api-producer")
                .given("the patron is 9 years old")
                .expectsToReceive("a rejected verification message")
                .withMetadata(Map.of("content-type", "some-mime-type"))
                .withContent(pactMessageContent)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createRejectedVerificationPact", providerType = ProviderType.ASYNCH)
    void should_call_beer_verification_service_when_verification_was_rejected(MessagePact verificationRejectedPact) {

        // when
        publishMessageToQueue(verificationRejectedPact);

        // assert
        BeerVerification verification = beerVerificationServiceMock.getVerificationInRequest();

        assertThat(verification, is(notNullValue()));

        assertThat(verification.getName(), is(equalTo("sebastian vasquez")));
        assertThat(verification.getIsApproved(), is(equalTo(false)));
        assertThat(verification.getBeersCount(), is(equalTo(0)));
        assertThat(verification.getCity(), is(equalTo("new york")));
        assertThat(verification.getDateOfBirth(), is(equalTo(Instant.parse("2011-02-15T15:15:15Z"))));
    }

    private void publishMessageToQueue(MessagePact messagePact) {

        List<Message> pactMessages = messagePact.getMessages();

        pactMessages.forEach(aPactMessage -> {
            byte[] protoBytes = getProtoBytes(aPactMessage);

            org.springframework.messaging.Message<byte[]> streamMessage = MessageBuilder.withPayload(protoBytes)
                    .setHeader("contentType", "application/some-custom-mime-type")
                    .build();

            sink.input().send(streamMessage);
        });
    }

    @SneakyThrows
    private byte[] getProtoBytes(Message aPactMessage) {
        String pactMessageContentString = aPactMessage.contentsAsString();
        JSONObject pactMessageContentJsonObject = new JSONObject(pactMessageContentString);
        String protoByteString = pactMessageContentJsonObject.getString("proto-byte-string");
        return Base64.getDecoder().decode(protoByteString);
    }

    private DslPart createPactDslPart(SomeCustomEnvelope appMessageEnvelopeProto) {

        byte[] appMessageEnvelopeProtoBytes = appMessageEnvelopeProto.toByteArray();
        byte[] appMessageEnvelopeProtoBytesBase64Bytes = Base64.getEncoder().encode(appMessageEnvelopeProtoBytes);
        String appMessageEnvelopeProtoBytesBase64String = new String(appMessageEnvelopeProtoBytesBase64Bytes);

        return new PactDslJsonBody().stringValue("proto-byte-string", appMessageEnvelopeProtoBytesBase64String);
    }
}

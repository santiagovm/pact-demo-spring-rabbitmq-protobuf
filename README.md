# Demo: Contract Testing with Pact for Spring Boot with Protobuf and RabbitMQ

This is a demo of Consumer-Driven Contract Testing using Pact with Spring Boot, Protobuf-based messages 
via Spring Cloud Stream and RabbitMQ.

## Demo Components
- **Consumer:** processes age verification messages from the producer with Pact-based tests that generate Pact files 
to share with the Producer
- **Producer:** sends messages to consumer indicating whether someone is old enough to drink beer. 
Uses the consumer-generated pacts for contract testing.

## Implementation Flow
At a high level the implementation flow looks like this:

1. The consumer works in contract definitions by writing pact-based tests in the consumer's codebase.
1. The consumer implements features to make the pact-based tests pass. As a side effect, pact files get automatically 
generated, which are then shared with the producer.
1. The producer writes a pact-based verifier that searches for pacts mentioning the provider.
1. When the provider runs the pact verifier errors appear indicating that pacts need to be honored.
1. The provider implements features to make the pact verifier pass.

## Defining Contracts
There are different ways to use Pact with Java, in this demo I use [pact-jvm-consumer-junit](https://docs.pact.io/implementation_guides/jvm/consumer/junit)

First, annotate the test class with `@ExtendWith(PactConsumerTestExt.class)` 

```java
@ExtendWith(PactConsumerTestExt.class)
class ConsumerContractTests {
    // ...
}
```

Then define a method for the `MessagePact` to use in the test. In the example below the pact will contain 
the `responseProto` encoded as a base64 string. The encoding logic is in the method `createPactDslPart`. 

```java
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
```

The base64 trick is used to transport the payload between pact generation and using the data in the test. 
Without the base64 encoding, the proto bytes get corrupted when they arrive to the test. 
The base64 string is used only in the tests, it is not part of payload traveling over the wire between 
producer and consumer via RabbitMQ.

```java
private DslPart createPactDslPart(SomeCustomEnvelope appMessageEnvelopeProto) {

    byte[] appMessageEnvelopeProtoBytes = appMessageEnvelopeProto.toByteArray();
    byte[] appMessageEnvelopeProtoBytesBase64Bytes = Base64.getEncoder().encode(appMessageEnvelopeProtoBytes);
    String appMessageEnvelopeProtoBytesBase64String = new String(appMessageEnvelopeProtoBytesBase64Bytes);

    return new PactDslJsonBody().stringValue("proto-byte-string", appMessageEnvelopeProtoBytesBase64String);
}
``` 

Then in the test add `@PactTestFor` annotation to connect the test with the method that generates the pact message. 
In the example below the test has access to the base64 payload in parameter `verificationAcceptedPact`. 
Since we are testing a queue consumer, the test uses the pact message to put a message 
in the queue the consumer is listening on, this happens in method `publishMessageToQueue`. 
The Subject Under Test is listening on the queue and configured to call our mock (beerVerificationServiceMock). 
We spy on that mock and verify the expected call was made. 

```java
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
```

The code below shows how the pact message is parsed and the proto payload is sent to the queue. 
Notice that the base64 string in the pact is decoded to obtain the proto bytes, and those are the ones 
sent to the queue, as expected the consumer will do.

```java
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
```

Run command below from the consumer folder to run consumer tests:

```shell script
$ ./mvnw test
```

## Transporting Pacts between Consumer and Producer
Once the pact-based tests pass, a pact file gets generated under `consumer/target/pacts`. 
Below is the pact generated for test we saw earlier. Notice the base64 representation of 
the proto file in the property `proto-byte-string`.

```json
{
  "consumer": {
    "name": "pact-beer-api-consumer"
  },
  "provider": {
    "name": "pact-beer-api-producer"
  },
  "messages": [
    {
      "description": "an accepted verification message",
      "metaData": {
        "contentType": "some-mime-type"
      },
      "contents": {
        "proto-byte-string": "ChBmb28tbWVzc2FnZS10eXBlEicKEHNhbnRpYWdvIHZhc3F1ZXoQARgHggEIbWVkZWxsaW6IAcDF+k4="
      },
      "providerStates": [
        {
          "name": "the patron is 45 years old"
        }
      ]
    }
  ],
  "metadata": {
    "pactSpecification": {
      "version": "3.0.0"
    },
    "pact-jvm": {
      "version": "4.1.7"
    }
  }
}
```

In a real situation, pact files are usually published by the consumer to a Pact Broker. 
For details of how to do this with using the Maven plugin go [here](https://docs.pact.io/implementation_guides/jvm/provider/maven/#publishing-pact-files-to-a-pact-broker). 
To keep things simple in this demo, I am copying the pact file to the producer codebase using script under `producer/scripts/copy-pacts-from-consumer.sh`.

## Verifying Contracts in the Producer Side
Annotate the class to run pact verifications as example below. In this case pacts will come from local folder `pacts`, 
this would need to change when using a Pact Broker.

```java
@RunWith(PactRunner.class)
@Provider("pact-beer-api-producer")
@PactFolder("pacts")
public class ProducerContractTests {
    // ...
}
```

Based on the pact above (the json file), the pact verifier will be looking for two methods, one decorated 
with state `the patron is 45 years old` and another with provider `an accepted verification message` like the ones in 
the example below. The technique used to verify the pact is to have the Subject Under Test (i.e., PersonCheckingService) 
generate the message to send to the consumer and return that as a String from the method annotated as `@PactVerifyProvider`. 
The pact verifier will compare this string with the payload in the pact file and throw an exception if they don't match.

To capture the message the producer will send to the queue, the test spies on the `MessagePublisher`. 
Notice that the payload captured when spying on the MessagePublisher (i.e. messageEnvelopeProtoBytes) 
is packaged in the same way the payload was packaged when the pact file was created. 
That is, the proto bytes are base64-encoded and then packaged into a json object under property `proto-byte-string`.

```java
private final MessagePublisher messagePublisher = Mockito.mock(MessagePublisher.class);

private final PersonCheckingService personCheckingService = new AgeCheckingPersonCheckingService(messagePublisher);

private int age;

@State("the patron is 45 years old")
public void the_patron_is_45_years_old() {
    age = 45;
}

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

private String createPactMessageContents(byte[] messageEnvelopeProtoBytes) {

    byte[] appMessageEnvelopeProtoBytesBase64Bytes = Base64.getEncoder().encode(messageEnvelopeProtoBytes);
    String appMessageEnvelopeProtoBytesBase64String = new String(appMessageEnvelopeProtoBytesBase64Bytes);

    return "{\"proto-byte-string\": \"" + appMessageEnvelopeProtoBytesBase64String + "\"}";
}
```

Run command below from the producer folder to run producer tests: 

```shell script
$ ./mvnw test
```

#### When Payload Does Not Match
One disadvantage of using protobuf in the payload is that pact cannot match individual elements withing 
the binary payload, it can only tell the base64 string is different to the expected one. 
See error message below for example.

```text
[ERROR] Failures:
[ERROR]   ProducerContractTests
Failures:

1) an accepted verification message generates a message which

    1.1) BodyMismatch: $.proto-byte-string BodyMismatch: $.proto-byte-string 
        Expected     'ChBmb28tbWVzc2FnZS10eXBlEiUKEXNlYmFzdGlhbiB2YXNxdWV6ggEIbmV3IHlvcmuIAYOx6uoE' (String) 
        but received 'ChBmb28tbWVzc2FnZS10eXBlEicKEHNhbnRpYWdvIHZhc3F1ZXoQARgHggEIbWVkZWxsaW6IAcDF+k4=' (String)
```

As a workaround to understand better the error details I put together a script that decodes the base64 string 
and the protobuf inside.

```shell script
$ ./producer/scripts/decode-proto-bytes.sh ChBmb28tbWVzc2FnZS10eXBlEicKEHNhbnRpYWdvIHZhc3F1ZXoQARgHggEIbWVkZWxsaW6IAcDF+k4=

===============[envelope decoded]===============

message_type: "foo-message-type"
event_data: "\n\020santiago vasquez\020\001\030\007\202\001\010medellin\210\001\300\305\372N"

===============[response decoded]===============

name: "santiago vasquez"
status: OK
beersCount: 7
city: "medellin"
dob: 165585600
``` 

## References
- [Pact Docs (pact-jvm-consumer-junit)](https://docs.pact.io/implementation_guides/jvm/consumer/junit)
- https://reflectoring.io/cdc-pact-messages/
- https://github.com/Mikuu/Pact-JVM-Example#23-publish-pacts-to-pact-broker
- [Maven Plugin to Publish Pact Files to Pact Broker](https://docs.pact.io/implementation_guides/jvm/provider/maven/#publishing-pact-files-to-a-pact-broker).

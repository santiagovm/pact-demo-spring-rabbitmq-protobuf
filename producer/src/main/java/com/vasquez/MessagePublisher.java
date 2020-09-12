package com.vasquez;

import org.springframework.cloud.stream.messaging.Source;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

public class MessagePublisher {

    private final Source source;

    public MessagePublisher(Source source) {
        this.source = source;
    }

    public void publishMessage(byte[] messageEnvelopeProtoBytes) {

        Message<byte[]> streamMessage = MessageBuilder.withPayload(messageEnvelopeProtoBytes)
                .setHeader("contentType", "application/some-custom-mime-type")
                .build();

        this.source.output().send(streamMessage);
    }
}

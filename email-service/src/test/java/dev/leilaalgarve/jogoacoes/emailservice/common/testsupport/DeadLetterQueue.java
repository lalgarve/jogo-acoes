package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

/**
 * The real LocalStack dead-letter queue of this suite's send queue ({@code email.dlq-name} in
 * src/test/resources/application.yml, spec 05-046). Tests put messages here the way email-lambda
 * does -- the message body plus the {@code errorType}/{@code errorMessage} attributes -- or
 * without attributes, the way SQS's redrive does.
 */
@Component
public class DeadLetterQueue {

    private final SqsAsyncClient sqsClient;
    private final String queueName;

    public DeadLetterQueue(SqsAsyncClient sqsClient, @Value("${email.dlq-name}") String queueName) {
        this.sqsClient = sqsClient;
        this.queueName = queueName;
    }

    public void ensureExists() {
        sqsClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build()).join();
    }

    /** As email-lambda sends it; {@code errorType == null} is a message SQS's redrive moved. */
    public void send(String body, String errorType) {
        SendMessageRequest.Builder request = SendMessageRequest.builder().queueUrl(queueUrl()).messageBody(body);
        if (errorType != null) {
            request.messageAttributes(Map.of(
                    "errorType", stringAttribute(errorType),
                    "errorMessage", stringAttribute("SES said no (" + errorType + ")")));
        }
        sqsClient.sendMessage(request.build()).join();
    }

    private static MessageAttributeValue stringAttribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }

    private String queueUrl() {
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).join().queueUrl();
    }
}

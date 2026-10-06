package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The real LocalStack send queue this suite publishes to (spec 05-031, T002). A queue of its own
 * ({@code email.queue-name} in src/test/resources/application.yml), not docker-compose's
 * {@code jogo-acoes-email-commands}: LocalStack wires that one to the deployed email-lambda, which
 * would consume the messages before a scenario could read them. Created here, on demand, and
 * emptied before every scenario ({@link SendQueueHooks}).
 *
 * <p>Every message read is deleted right away: one left in flight would become visible again
 * after its visibility timeout and show up in a later scenario's "nothing is put on the queue".
 */
@Component
public class SendQueue {

    private final SqsAsyncClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueName;

    public SendQueue(SqsAsyncClient sqsClient, ObjectMapper objectMapper,
                     @Value("${email.queue-name}") String queueName) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueName = queueName;
    }

    /** Idempotent: CreateQueue with the same name and attributes returns the existing queue. */
    public void ensureExists() {
        sqsClient.createQueue(CreateQueueRequest.builder().queueName(queueName).build()).join();
    }

    /** Makes publishing fail for real: the service's next send hits a queue that no longer exists. */
    public void delete() {
        sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queueUrl()).build()).join();
    }

    public void drain() {
        while (!receiveAndDelete(0).isEmpty()) {
            // keep going until a receive comes back empty
        }
    }

    /** Every message on the queue, waiting up to {@code waitSeconds} for the first one. */
    public List<JsonNode> receiveAll(int waitSeconds) {
        List<JsonNode> all = new ArrayList<>(receiveAndDelete(waitSeconds));
        List<JsonNode> more;
        while (!(more = receiveAndDelete(0)).isEmpty()) {
            all.addAll(more);
        }
        return all;
    }

    private List<JsonNode> receiveAndDelete(int waitSeconds) {
        String queueUrl = queueUrl();
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(waitSeconds)
                .build()).join().messages();
        List<JsonNode> bodies = new ArrayList<>();
        for (Message message : messages) {
            bodies.add(objectMapper.readTree(message.body()));
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build()).join();
        }
        return bodies;
    }

    private String queueUrl() {
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).join().queueUrl();
    }
}

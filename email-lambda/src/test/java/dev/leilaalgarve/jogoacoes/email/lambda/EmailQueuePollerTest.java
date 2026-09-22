package dev.leilaalgarve.jogoacoes.email.lambda;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises EmailQueuePoller.pollOnce (a single receive/handle/delete cycle) against a mocked
 * SqsClient/EmailSendHandler -- no LocalStack, no Docker, no network. This is the piece the
 * module's other test (EmailSendHandlerTest) doesn't cover: that test proves the SES send logic
 * works against a real (simulated) SES, but skips entirely without Docker; it says nothing
 * about whether this class actually wires "receive from SQS -> call the handler -> delete only
 * on success" correctly. That wiring is exactly what this test proves, deterministically, every
 * run, regardless of Docker availability.
 */
@ExtendWith(MockitoExtension.class)
class EmailQueuePollerTest {

    private static final String QUEUE_URL = "http://localhost:4566/000000000000/jogo-acoes-email-commands";

    @Mock
    private SqsClient sqsClient;

    @Mock
    private EmailSendHandler handler;

    private EmailQueuePoller poller;

    @BeforeEach
    void setUp() {
        poller = new EmailQueuePoller();
        poller.sqsClient = sqsClient;
        poller.handler = handler;
    }

    @Test
    void deletesTheMessageOnlyAfterTheHandlerSucceeds() {
        Message message = Message.builder().body("{\"schemaVersion\":\"1\"}").receiptHandle("receipt-1").build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build());

        poller.pollOnce(QUEUE_URL);

        verify(handler).handleRequest(any(SQSEvent.class), isNull());
        verify(sqsClient).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle("receipt-1")
                .build());
    }

    @Test
    void leavesTheMessageInTheQueueWhenTheHandlerFails() {
        Message message = Message.builder().body("not-json").receiptHandle("receipt-2").build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build());
        doThrow(new IllegalArgumentException("malformed message")).when(handler).handleRequest(any(SQSEvent.class), isNull());

        poller.pollOnce(QUEUE_URL);

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void doesNothingWhenTheQueueIsEmpty() {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        poller.pollOnce(QUEUE_URL);

        verify(handler, never()).handleRequest(any(), isNull());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void sendsTheMessageBodyThroughToTheHandlerUnchanged() {
        Message message = Message.builder().body("the-raw-queue-body").receiptHandle("receipt-3").build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of(message)).build());
        ArgumentCaptor<SQSEvent> captor = ArgumentCaptor.forClass(SQSEvent.class);

        poller.pollOnce(QUEUE_URL);

        verify(handler).handleRequest(captor.capture(), isNull());
        assertThat(captor.getValue().getRecords()).hasSize(1);
        assertThat(captor.getValue().getRecords().get(0).getBody()).isEqualTo("the-raw-queue-body");
    }

    @Test
    void processesEachMessageIndependentlySoOneFailureDoesNotBlockTheOthers() {
        Message failing = Message.builder().body("bad").receiptHandle("receipt-fail").build();
        Message succeeding = Message.builder().body("good").receiptHandle("receipt-ok").build();
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of(failing, succeeding)).build());
        ArgumentCaptor<SQSEvent> captor = ArgumentCaptor.forClass(SQSEvent.class);
        doThrow(new IllegalArgumentException("bad")).doNothing().when(handler).handleRequest(captor.capture(), isNull());

        poller.pollOnce(QUEUE_URL);

        assertThat(captor.getAllValues()).hasSize(2);
        verify(sqsClient, never()).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL).receiptHandle("receipt-fail").build());
        verify(sqsClient).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL).receiptHandle("receipt-ok").build());
    }
}

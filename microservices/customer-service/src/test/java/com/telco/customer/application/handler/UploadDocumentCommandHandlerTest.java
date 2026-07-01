package com.telco.customer.application.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telco.customer.application.AuditLogWriter;
import com.telco.customer.application.command.UploadDocumentCommand;
import com.telco.customer.domain.Document;
import com.telco.customer.domain.DocumentType;
import com.telco.customer.infrastructure.persistence.CustomerRepository;
import com.telco.customer.infrastructure.persistence.DocumentRepository;
import com.telco.customer.infrastructure.storage.DocumentStorage;
import com.telco.platform.common.exception.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UploadDocumentCommandHandlerTest {

    @Mock
    private CustomerRepository customers;
    @Mock
    private DocumentRepository documents;
    @Mock
    private DocumentStorage storage;
    @Mock
    private AuditLogWriter audit;

    private UploadDocumentCommandHandler handler;

    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new UploadDocumentCommandHandler(customers, documents, storage, audit);
    }

    @Test
    void rejectsUnsupportedContentType() {
        when(customers.existsById(customerId)).thenReturn(true);
        UploadDocumentCommand command = new UploadDocumentCommand(
                customerId, DocumentType.ID_CARD, "id.txt", "text/plain", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(ValidationException.class);
        verify(storage, never()).store(anyString(), any(), anyString());
    }

    @Test
    void rejectsOversizedDocument() {
        when(customers.existsById(customerId)).thenReturn(true);
        byte[] tooBig = new byte[6 * 1024 * 1024];
        UploadDocumentCommand command = new UploadDocumentCommand(
                customerId, DocumentType.ID_CARD, "id.png", "image/png", tooBig);

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(ValidationException.class);
        verify(storage, never()).store(anyString(), any(), anyString());
    }

    @Test
    void storesValidDocumentAndRecordsReference() {
        when(customers.existsById(customerId)).thenReturn(true);
        when(documents.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        UploadDocumentCommand command = new UploadDocumentCommand(
                customerId, DocumentType.ID_CARD, "id.png", "image/png", new byte[] {9, 8, 7});

        var response = handler.handle(command);

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.type()).isEqualTo("ID_CARD");
        verify(storage).store(anyString(), eq(command.content()), eq("image/png"));
        verify(documents).save(any(Document.class));
    }
}

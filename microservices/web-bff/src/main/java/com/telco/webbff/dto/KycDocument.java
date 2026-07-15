package com.telco.webbff.dto;

/**
 * KYC document carried by the onboarding order request. The wizard collects the document once and the
 * BFF forwards it as a multipart upload to customer-service
 * ({@code POST /api/v1/customers/{id}/documents}), which stores the binary in MinIO and returns the
 * object reference (fileRef). {@code content} is Base64-encoded so the document travels inside the
 * JSON order request; the BFF decodes it and streams the bytes on to the gateway without persisting
 * anything (ADR-022: no persistence in the BFF).
 *
 * @param type        document type accepted by customer-service (e.g. {@code ID_CARD}, {@code PASSPORT})
 * @param fileName    original file name, forwarded as the multipart part filename
 * @param contentType MIME type of the document
 * @param content     Base64-encoded document bytes
 */
public record KycDocument(
        String type,
        String fileName,
        String contentType,
        String content) {
}

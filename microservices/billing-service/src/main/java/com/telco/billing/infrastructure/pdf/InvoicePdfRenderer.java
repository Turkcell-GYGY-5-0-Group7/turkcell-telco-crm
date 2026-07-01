package com.telco.billing.infrastructure.pdf;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.telco.billing.domain.Invoice;
import com.telco.billing.domain.InvoiceLine;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class InvoicePdfRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
            .withZone(ZoneOffset.UTC);

    public byte[] render(Invoice invoice) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4);

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            doc.add(new Paragraph("INVOICE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Invoice ID: " + invoice.getId()));
            doc.add(new Paragraph("Customer ID: " + invoice.getCustomerId()));
            doc.add(new Paragraph("Subscription ID: " + invoice.getSubscriptionId()));
            doc.add(new Paragraph("Period: " + DATE_FMT.format(invoice.getPeriodStart())
                    + " to " + DATE_FMT.format(invoice.getPeriodEnd())));
            doc.add(new Paragraph("Status: " + invoice.getStatus()));
            doc.add(new Paragraph("Due Date: " + invoice.getDueDate()));
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("INVOICE LINES", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            for (InvoiceLine line : invoice.getLines()) {
                doc.add(new Paragraph(String.format("  %s  qty=%s  unit=%s %s  total=%s %s",
                        line.getDescription(),
                        line.getQuantity().toPlainString(),
                        line.getUnitPrice().toPlainString(), invoice.getCurrency(),
                        line.getLineTotal().toPlainString(), invoice.getCurrency())));
            }

            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Subtotal: " + invoice.getSubTotal().toPlainString() + " " + invoice.getCurrency()));
            doc.add(new Paragraph("Tax: " + invoice.getTax().toPlainString() + " " + invoice.getCurrency()));
            doc.add(new Paragraph(
                    "Grand Total: " + invoice.getGrandTotal().toPlainString() + " " + invoice.getCurrency(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
        } finally {
            doc.close();
        }

        return out.toByteArray();
    }
}

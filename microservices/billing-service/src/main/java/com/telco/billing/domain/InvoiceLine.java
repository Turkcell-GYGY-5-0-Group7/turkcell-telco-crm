package com.telco.billing.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    protected InvoiceLine() {}

    public static InvoiceLine of(Invoice invoice, String description,
                                 BigDecimal quantity, BigDecimal unitPrice) {
        InvoiceLine line = new InvoiceLine();
        line.id = UUID.randomUUID();
        line.invoice = invoice;
        line.description = description;
        line.quantity = quantity;
        line.unitPrice = unitPrice;
        line.lineTotal = quantity.multiply(unitPrice);
        invoice.addLine(line);
        return line;
    }

    public UUID getId()             { return id; }
    public Invoice getInvoice()     { return invoice; }
    public String getDescription()  { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
}

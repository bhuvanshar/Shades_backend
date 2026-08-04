package com.sunglassstore.service;

import com.sunglassstore.dto.response.AdminOrderResponse;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.service.impl.InvoiceServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceServiceImplTest {
    private final InvoiceServiceImpl service = new InvoiceServiceImpl(
            "Shades World Barcelona", "support@shadesworld.test", "Barcelona, Spain");

    @Test
    void paidOrderProducesReadableMultipagePdfWithSnapshotAmounts() throws Exception {
        AdminOrderResponse order = order("PAID", 28);

        byte[] bytes = service.generate(order);

        assertTrue(bytes.length > 2_000);
        assertEquals("%PDF", new String(bytes, 0, 4));
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(document.getNumberOfPages() > 1);
            assertTrue(text.contains("INVOICE"));
            assertTrue(text.contains("Invoice no. SW-42"));
            assertTrue(text.contains("Customer One"));
            assertTrue(text.contains("Classic Frame 1"));
            assertTrue(text.contains("INR 2478.20"));
            assertTrue(text.contains("MOCK-PAID-42"));
        }
    }

    @Test
    void unpaidOrderCannotGenerateInvoice() {
        assertThrows(BadRequestException.class, () -> service.generate(order("PENDING", 1)));
    }

    private AdminOrderResponse order(String paymentStatus, int itemCount) {
        LocalDateTime purchasedAt = LocalDateTime.of(2026, 8, 4, 12, 30);
        List<AdminOrderResponse.Item> items = new ArrayList<>();
        for (int index = 1; index <= itemCount; index++) {
            items.add(new AdminOrderResponse.Item((long) index, "Classic Frame " + index,
                    "SW-CF-" + index, 1, new BigDecimal("75.00"), new BigDecimal("13.50"),
                    BigDecimal.ZERO, new BigDecimal("75.00")));
        }
        return new AdminOrderResponse(42L, "CONFIRMED", new BigDecimal("2100.00"),
                new BigDecimal("100.00"), new BigDecimal("360.00"), new BigDecimal("18.20"),
                new BigDecimal("2478.20"), purchasedAt, null, purchasedAt,
                new AdminOrderResponse.Customer(7L, "Customer One", "customer@example.com", "9999999999"),
                new AdminOrderResponse.ShippingAddress("Customer One", "9999999999", "12 Long Street",
                        "Apartment 4", "Barcelona", "Catalonia", "08001", "Spain"),
                items,
                List.of(new AdminOrderResponse.PaymentInfo(3L, new BigDecimal("2478.20"), "MOCK",
                        paymentStatus, "MOCK", "MOCK-PAID-42", purchasedAt, purchasedAt)),
                List.of(), List.of());
    }
}

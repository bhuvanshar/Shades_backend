package com.sunglassstore.service.impl;

import com.sunglassstore.dto.response.AdminOrderResponse;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.service.InvoiceService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class InvoiceServiceImpl implements InvoiceService {
    private static final Set<String> INVOICE_PAYMENT_STATUSES = Set.of("PAID", "PARTIALLY_REFUNDED", "REFUNDED");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDColor INK = new PDColor(new float[]{35 / 255f, 48 / 255f, 40 / 255f}, PDDeviceRGB.INSTANCE);
    private static final PDColor MUTED = new PDColor(new float[]{105 / 255f, 105 / 255f, 98 / 255f}, PDDeviceRGB.INSTANCE);
    private static final PDColor SAND = new PDColor(new float[]{232 / 255f, 224 / 255f, 210 / 255f}, PDDeviceRGB.INSTANCE);

    private final String sellerName;
    private final String sellerEmail;
    private final String sellerAddress;

    public InvoiceServiceImpl(
            @Value("${invoice.seller.name:Shades World Barcelona}") String sellerName,
            @Value("${invoice.seller.email:support@shadesworld.example}") String sellerEmail,
            @Value("${invoice.seller.address:Seller address to be configured}") String sellerAddress) {
        this.sellerName = sellerName;
        this.sellerEmail = sellerEmail;
        this.sellerAddress = sellerAddress;
    }

    @Override
    public byte[] generate(AdminOrderResponse order) {
        if (order == null) throw new BadRequestException("Order is required");
        boolean paid = order.payments() != null && order.payments().stream()
                .anyMatch(payment -> INVOICE_PAYMENT_STATUSES.contains(payment.status()));
        if (!paid) throw new BadRequestException("Invoice is available after payment is recorded");

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            InvoiceCanvas canvas = new InvoiceCanvas(document, order);
            canvas.header();
            canvas.parties();
            canvas.items(order.items());
            canvas.totals();
            canvas.payment();
            canvas.footer();
            canvas.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate invoice", exception);
        }
    }

    private final class InvoiceCanvas {
        private final PDDocument document;
        private final AdminOrderResponse order;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        private InvoiceCanvas(PDDocument document, AdminOrderResponse order) throws IOException {
            this.document = document;
            this.order = order;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = 790;
            pageNumber++;
        }

        private void header() throws IOException {
            stream.setNonStrokingColor(SAND);
            stream.addRect(0, 728, PDRectangle.A4.getWidth(), 114);
            stream.fill();
            text(sellerName.toUpperCase(), 46, 792, BOLD, 17, INK);
            text("EYEWEAR / BARCELONA", 46, 773, BOLD, 7, MUTED);
            text("INVOICE", 438, 791, BOLD, 20, INK);
            text("Invoice no. SW-" + order.orderId(), 438, 770, REGULAR, 8, MUTED);
            text("Issued " + DATE.format(order.purchasedAt()), 438, 756, REGULAR, 8, MUTED);
            y = 700;
        }

        private void parties() throws IOException {
            label("FROM", 46, y);
            text(sellerName, 46, y - 18, BOLD, 10, INK);
            wrapped(sellerAddress, 46, y - 34, 220, REGULAR, 8, MUTED, 11);
            text(sellerEmail, 46, y - 68, REGULAR, 8, MUTED);

            label("BILL TO / SHIP TO", 316, y);
            AdminOrderResponse.ShippingAddress address = order.shippingAddress();
            text(address.name(), 316, y - 18, BOLD, 10, INK);
            float addressY = wrapped(address.line1() + optional(", " + address.line2(), address.line2()),
                    316, y - 34, 230, REGULAR, 8, MUTED, 11);
            text(address.city() + ", " + address.state() + " " + address.pincode(), 316, addressY, REGULAR, 8, MUTED);
            text(address.country() + optional(" / " + address.phone(), address.phone()), 316, addressY - 13, REGULAR, 8, MUTED);
            y -= 112;
        }

        private void items(List<AdminOrderResponse.Item> items) throws IOException {
            tableHeader();
            for (AdminOrderResponse.Item item : items) {
                if (y < 105) {
                    footer();
                    newPage();
                    text("INVOICE SW-" + order.orderId() + " - CONTINUED", 46, 800, BOLD, 11, INK);
                    y = 770;
                    tableHeader();
                }
                float rowTop = y;
                text(clip(item.productName(), 37), 46, rowTop - 18, BOLD, 9, INK);
                text("SKU " + clip(item.sku(), 28), 46, rowTop - 32, REGULAR, 7, MUTED);
                right(String.valueOf(item.quantity()), 354, rowTop - 20, REGULAR, 9, INK);
                right(amount(item.unitPrice()), 447, rowTop - 20, REGULAR, 9, INK);
                right(amount(item.lineTotal()), 549, rowTop - 20, BOLD, 9, INK);
                line(46, rowTop - 43, 549, rowTop - 43);
                y -= 44;
            }
            y -= 12;
        }

        private void tableHeader() throws IOException {
            stream.setNonStrokingColor(INK);
            stream.addRect(46, y - 28, 503, 28);
            stream.fill();
            text("ITEM", 56, y - 18, BOLD, 8, white());
            right("QTY", 354, y - 18, BOLD, 8, white());
            right("UNIT PRICE", 447, y - 18, BOLD, 8, white());
            right("AMOUNT", 539, y - 18, BOLD, 8, white());
            y -= 28;
        }

        private void totals() throws IOException {
            ensure(150);
            float x = 355;
            totalRow("Subtotal", order.subtotalAmount(), x, false);
            totalRow("Discount", order.discountAmount().negate(), x, false);
            totalRow("Shipping", order.shippingAmount(), x, false);
            totalRow("Tax", order.taxAmount(), x, false);
            line(x, y - 4, 549, y - 4);
            y -= 22;
            totalRow("TOTAL", order.totalAmount(), x, true);
            y -= 10;
        }

        private void totalRow(String name, BigDecimal value, float x, boolean bold) throws IOException {
            text(name, x, y, bold ? BOLD : REGULAR, bold ? 11 : 9, bold ? INK : MUTED);
            right(amount(value), 549, y, bold ? BOLD : REGULAR, bold ? 12 : 9, INK);
            y -= bold ? 24 : 18;
        }

        private void payment() throws IOException {
            ensure(100);
            AdminOrderResponse.PaymentInfo payment = order.payments().stream()
                    .filter(item -> INVOICE_PAYMENT_STATUSES.contains(item.status())).findFirst().orElseThrow();
            label("PAYMENT", 46, y);
            text(payment.method() + " / " + payment.status(), 46, y - 18, BOLD, 9, INK);
            text("Reference: " + (payment.reference() == null ? "Not supplied" : payment.reference()), 46, y - 34, REGULAR, 8, MUTED);
            text("Order status: " + order.orderStatus(), 316, y - 18, BOLD, 9, INK);
            text("Customer: " + order.customer().email(), 316, y - 34, REGULAR, 8, MUTED);
            y -= 65;
        }

        private void footer() throws IOException {
            text("Thank you for choosing Shades World Barcelona.", 46, 44, REGULAR, 8, MUTED);
            right("Page " + pageNumber, 549, 44, REGULAR, 8, MUTED);
        }

        private void ensure(float height) throws IOException {
            if (y - height < 70) {
                footer();
                newPage();
                y = 780;
            }
        }

        private float wrapped(String value, float x, float startY, float width, PDFont font,
                              float size, PDColor color, float leading) throws IOException {
            String[] words = safe(value).split("\\s+");
            StringBuilder line = new StringBuilder();
            float currentY = startY;
            for (String word : words) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (font.getStringWidth(candidate) / 1000 * size > width && !line.isEmpty()) {
                    text(line.toString(), x, currentY, font, size, color);
                    currentY -= leading;
                    line = new StringBuilder(word);
                } else line = new StringBuilder(candidate);
            }
            if (!line.isEmpty()) text(line.toString(), x, currentY, font, size, color);
            return currentY - leading;
        }

        private void label(String value, float x, float atY) throws IOException { text(value, x, atY, BOLD, 7, MUTED); }
        private void text(String value, float x, float atY, PDFont font, float size, PDColor color) throws IOException {
            stream.beginText(); stream.setFont(font, size); stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, atY); stream.showText(asPdfText(value)); stream.endText();
        }
        private void right(String value, float right, float atY, PDFont font, float size, PDColor color) throws IOException {
            float width = font.getStringWidth(asPdfText(value)) / 1000 * size;
            text(value, right - width, atY, font, size, color);
        }
        private void line(float x1, float y1, float x2, float y2) throws IOException {
            stream.setStrokingColor(new PDColor(new float[]{.87f, .86f, .82f}, PDDeviceRGB.INSTANCE));
            stream.setLineWidth(.5f); stream.moveTo(x1, y1); stream.lineTo(x2, y2); stream.stroke();
        }
        private void close() throws IOException { stream.close(); }
    }

    private static String amount(BigDecimal value) {
        return "INR " + (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static String optional(String decorated, String value) { return value == null || value.isBlank() ? "" : decorated; }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String clip(String value, int max) { String safe = safe(value); return safe.length() <= max ? safe : safe.substring(0, max - 3) + "..."; }
    private static String asPdfText(String value) { return safe(value).replaceAll("[^\\x20-\\x7E]", "?"); }
    private static PDColor white() { return new PDColor(new float[]{1, 1, 1}, PDDeviceRGB.INSTANCE); }
}

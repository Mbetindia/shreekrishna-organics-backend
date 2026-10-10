package com.shreekrishna.organics.order.service;

import com.shreekrishna.organics.order.entity.Order;
import com.shreekrishna.organics.order.entity.OrderItem;
import com.shreekrishna.organics.order.repository.OrderItemRepository;
import com.shreekrishna.organics.order.repository.OrderRepository;
import com.shreekrishna.organics.user.repository.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OrderConfirmationPdfService {
    private final UserRepository users;
    private final OrderRepository orders;
    private final OrderItemRepository items;

    public OrderConfirmationPdfService(UserRepository users, OrderRepository orders,
                                       OrderItemRepository items) {
        this.users = users;
        this.orders = orders;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public byte[] generate(String email, Long orderId) {
        Long userId = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"))
                .getId();
        Order order = orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        List<OrderItem> lines = items.findByOrder_IdOrderByIdAsc(orderId);

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(document);
            writer.line("SHREEKRISHNA ORGANICS", 17);
            writer.line("ORDER CONFIRMATION - NOT A TAX INVOICE", 12);
            writer.space(8);
            writer.line("Order No: " + order.getOrderNumber(), 11);
            writer.line("Placed: " + (order.getCreatedAt() == null ? "Pending" :
                    order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))), 10);
            writer.line("Order Status: " + order.getStatus(), 10);
            writer.line("Payment: " + order.getPaymentMethod() + " / " + order.getPaymentStatus(), 10);
            writer.space(10);
            writer.line("DELIVER TO", 12);
            writer.line(order.getShippingFullName() + " | " + order.getShippingMobile(), 10);
            writer.line(order.getShippingAddressLine1(), 10);
            if (hasText(order.getShippingAddressLine2())) writer.line(order.getShippingAddressLine2(), 10);
            if (hasText(order.getShippingLandmark())) writer.line(order.getShippingLandmark(), 10);
            writer.line(order.getShippingCity() + ", " + order.getShippingState() + " - " +
                    order.getShippingPostalCode(), 10);
            writer.line(order.getShippingCountry(), 10);
            writer.space(12);
            writer.line("ITEMS", 12);
            for (OrderItem item : lines) {
                writer.line(item.getProductName() + " (" + item.getSizeValue() + " " +
                        item.getSizeUnit() + ")", 10);
                writer.line("  SKU: " + item.getSku() + " | Qty: " + item.getQuantity() +
                        " | Unit: INR " + money(item.getUnitPrice()) +
                        " | Amount: INR " + money(item.getLineTotal()), 9);
                writer.space(4);
            }
            writer.space(10);
            writer.line("Subtotal: INR " + money(order.getSubtotal()), 10);
            writer.line("Delivery: INR " + money(order.getDeliveryCharge()), 10);
            writer.line("Discount: INR " + money(order.getDiscountAmount()), 10);
            writer.line("Tax recorded on order: INR " + money(order.getTaxAmount()), 10);
            writer.line("TOTAL: INR " + money(order.getTotalAmount()), 13);
            writer.space(15);
            writer.line("This confirms your order. It is not a GST tax invoice or payment receipt.", 9);
            writer.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to generate order confirmation PDF", ex);
        }
    }

    private static boolean hasText(String s) { return s != null && !s.isBlank(); }
    private static String money(BigDecimal value) { return value == null ? "0.00" : value.toPlainString(); }

    private static class PdfWriter {
        private final PDDocument doc;
        private PDPageContentStream stream;
        private float y;
        private static final float LEFT = 45;
        private static final float BOTTOM = 45;
        private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * LEFT;
        private final PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        PdfWriter(PDDocument doc) throws IOException { this.doc = doc; newPage(); }

        void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = PDRectangle.A4.getHeight() - 48;
        }

        void space(float amount) throws IOException { y -= amount; if (y < BOTTOM + 15) newPage(); }

        void line(String input, int size) throws IOException {
            String remaining = ascii(input == null ? "" : input);
            if (remaining.isEmpty()) { write(" ", size); return; }
            while (!remaining.isEmpty()) {
                int cut = remaining.length();
                while (cut > 1 && font.getStringWidth(remaining.substring(0, cut)) / 1000f * size > WIDTH) cut--;
                if (cut < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', cut);
                    if (space > 0) cut = space;
                }
                write(remaining.substring(0, cut).strip(), size);
                remaining = remaining.substring(cut).stripLeading();
            }
        }

        private void write(String value, int size) throws IOException {
            if (y < BOTTOM + size + 5) newPage();
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(LEFT, y);
            stream.showText(value.isEmpty() ? " " : value);
            stream.endText();
            y -= size + 6;
        }

        private static String ascii(String s) {
            // Standard PDF fonts support basic Latin. Use an embedded Unicode font
            // in a future enhancement for Marathi/Devanagari invoice content.
            return s.replaceAll("[^\\x20-\\x7E]", "?");
        }

        void close() throws IOException { if (stream != null) { stream.close(); stream = null; } }
    }
}

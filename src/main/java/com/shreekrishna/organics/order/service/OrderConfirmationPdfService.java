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
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
        List<OrderItem> orderItems = items.findByOrder_IdOrderByIdAsc(orderId);

        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Layout pdf = new Layout(document);
            try {
                pdf.drawHeader(order);
                pdf.drawCustomer(order);
                pdf.drawItems(orderItems);
                pdf.drawTotals(order);
                pdf.drawClosingNote(order);
            } finally {
                pdf.close();
            }
            pdf.drawPageFooters();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to generate order confirmation PDF", ex);
        }
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String safe(Object value) {
        if (value == null) return "-";
        String s = value.toString().trim();
        if (s.isEmpty()) return "-";
        // Standard PDF fonts only support a limited character set.
        return s.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static final class Layout implements AutoCloseable {
        private static final float PAGE_W = PDRectangle.A4.getWidth();
        private static final float PAGE_H = PDRectangle.A4.getHeight();
        private static final float MARGIN = 43;
        private static final float CONTENT_W = PAGE_W - 2 * MARGIN;
        private static final float BOTTOM = 68;
        private static final float TABLE_ROW_PAD = 10;

        private static final float[] GREEN = {0.16f, 0.36f, 0.25f};
        private static final float[] GOLD = {0.74f, 0.55f, 0.23f};
        private static final float[] DARK = {0.16f, 0.19f, 0.17f};
        private static final float[] MUTED = {0.43f, 0.47f, 0.44f};
        private static final float[] LIGHT = {0.95f, 0.97f, 0.94f};
        private static final float[] LINE = {0.85f, 0.88f, 0.85f};
        private static final float[] WHITE = {1f, 1f, 1f};

        private final PDDocument doc;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        Layout(PDDocument doc) throws IOException {
            this.doc = doc;
            nextPage();
        }

        private void nextPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            pageNumber++;
            y = PAGE_H - 45;
            if (pageNumber > 1) {
                text("SHREEKRISHNA ORGANICS", MARGIN, y, 10, bold, GREEN);
                y -= 14;
                rule(y, LINE);
                y -= 20;
            }
        }

        private void ensure(float requiredHeight) throws IOException {
            if (y - requiredHeight < BOTTOM) nextPage();
        }

        private void fillRect(float x, float bottomY, float width, float height, float[] color)
                throws IOException {
            stream.setNonStrokingColor(color[0], color[1], color[2]);
            stream.addRect(x, bottomY, width, height);
            stream.fill();
        }

        private void rule(float atY, float[] color) throws IOException {
            stream.setStrokingColor(color[0], color[1], color[2]);
            stream.setLineWidth(0.7f);
            stream.moveTo(MARGIN, atY);
            stream.lineTo(PAGE_W - MARGIN, atY);
            stream.stroke();
        }

        private float width(String text, float size, PDFont font) throws IOException {
            return font.getStringWidth(safe(text)) * size / 1000f;
        }

        private void text(String value, float x, float baseline, float size, PDFont font,
                          float[] color) throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(color[0], color[1], color[2]);
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(safe(value));
            stream.endText();
        }

        private void rightText(String value, float right, float baseline, float size,
                               PDFont font, float[] color) throws IOException {
            text(value, right - width(value, size, font), baseline, size, font, color);
        }

        private List<String> wrap(String value, float maxWidth, float size, PDFont font)
                throws IOException {
            String remaining = safe(value);
            List<String> lines = new ArrayList<>();
            while (!remaining.isEmpty()) {
                if (width(remaining, size, font) <= maxWidth) {
                    lines.add(remaining);
                    break;
                }
                int cut = remaining.length();
                while (cut > 1 && width(remaining.substring(0, cut), size, font) > maxWidth) cut--;
                int space = remaining.lastIndexOf(' ', cut);
                if (space > 0) cut = space;
                lines.add(remaining.substring(0, cut).strip());
                remaining = remaining.substring(cut).stripLeading();
            }
            if (lines.isEmpty()) lines.add("-");
            return lines;
        }

        private float wrappedText(String value, float x, float topBaseline,
                                  float maxWidth, float size, PDFont font,
                                  float[] color, float leading) throws IOException {
            float baseline = topBaseline;
            for (String line : wrap(value, maxWidth, size, font)) {
                text(line, x, baseline, size, font, color);
                baseline -= leading;
            }
            return baseline;
        }

        void drawHeader(Order order) throws IOException {
            fillRect(0, PAGE_H - 131, PAGE_W, 131, GREEN);
            fillRect(MARGIN, PAGE_H - 135, 95, 4, GOLD);
            text("SHREEKRISHNA", MARGIN, PAGE_H - 59, 20, bold, WHITE);
            text("ORGANICS", MARGIN, PAGE_H - 81, 16, bold, WHITE);
            text("Connect with nature and boost your health", MARGIN,
                    PAGE_H - 101, 8, regular, WHITE);
            rightText("ORDER", PAGE_W - MARGIN, PAGE_H - 61, 17, bold, WHITE);
            rightText("CONFIRMATION", PAGE_W - MARGIN, PAGE_H - 80, 11, bold, WHITE);
            rightText("NOT A TAX INVOICE", PAGE_W - MARGIN, PAGE_H - 99, 8, regular, WHITE);
            y = PAGE_H - 159;

            text("ORDER REFERENCE", MARGIN, y, 8, bold, MUTED);
            text("ORDER DATE", MARGIN + 260, y, 8, bold, MUTED);
            y -= 17;
            y = Math.min(
                    wrappedText(order.getOrderNumber(), MARGIN, y, 250, 10, bold, DARK, 13),
                    y - 1);
            String date = order.getCreatedAt() == null ? "Pending" :
                    order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"));
            text(date, MARGIN + 260, y + 13, 10, regular, DARK);
            y -= 18;
            rule(y, LINE);
            y -= 23;
        }

        void drawCustomer(Order order) throws IOException {
            ensure(150);
            text("DELIVERY DETAILS", MARGIN, y, 10, bold, GREEN);
            text("ORDER & PAYMENT", MARGIN + 282, y, 10, bold, GREEN);
            y -= 19;
            float leftY = y;
            leftY = wrappedText(order.getShippingFullName(), MARGIN, leftY,
                    245, 10, bold, DARK, 14);
            leftY = wrappedText(order.getShippingMobile(), MARGIN, leftY,
                    245, 9, regular, MUTED, 13);
            leftY = wrappedText(order.getShippingAddressLine1(), MARGIN, leftY,
                    245, 9, regular, DARK, 13);
            if (hasText(order.getShippingAddressLine2()))
                leftY = wrappedText(order.getShippingAddressLine2(), MARGIN, leftY,
                        245, 9, regular, DARK, 13);
            if (hasText(order.getShippingLandmark()))
                leftY = wrappedText(order.getShippingLandmark(), MARGIN, leftY,
                        245, 9, regular, DARK, 13);
            leftY = wrappedText(safe(order.getShippingCity()) + ", " +
                            safe(order.getShippingState()) + " - " + safe(order.getShippingPostalCode()),
                    MARGIN, leftY, 245, 9, regular, DARK, 13);
            leftY = wrappedText(order.getShippingCountry(), MARGIN, leftY,
                    245, 9, regular, DARK, 13);

            float rightY = y;
            rightY = detail("Order status", safe(order.getStatus()), rightY);
            rightY = detail("Payment method", safe(order.getPaymentMethod()), rightY);
            rightY = detail("Payment status", safe(order.getPaymentStatus()), rightY);
            y = Math.min(leftY, rightY) - 16;
            ensure(40);
            rule(y, LINE);
            y -= 27;
        }

        private float detail(String label, String value, float baseline) throws IOException {
            text(label, MARGIN + 282, baseline, 9, regular, MUTED);
            rightText(value, PAGE_W - MARGIN, baseline, 9, bold, DARK);
            return baseline - 20;
        }

        private void tableHeader() throws IOException {
            ensure(35);
            fillRect(MARGIN, y - 27, CONTENT_W, 31, GREEN);
            text("PRODUCT / VARIANT", MARGIN + 11, y - 16, 9, bold, WHITE);
            rightText("QTY", MARGIN + 347, y - 16, 9, bold, WHITE);
            rightText("RATE", MARGIN + 424, y - 16, 9, bold, WHITE);
            rightText("AMOUNT", PAGE_W - MARGIN - 10, y - 16, 9, bold, WHITE);
            y -= 31;
        }

        void drawItems(List<OrderItem> orderItems) throws IOException {
            ensure(50);
            text("ORDER ITEMS", MARGIN, y, 11, bold, GREEN);
            y -= 19;
            tableHeader();
            if (orderItems.isEmpty()) {
                text("No items found", MARGIN + 10, y - 19, 9, regular, MUTED);
                y -= 43;
                return;
            }
            for (int index = 0; index < orderItems.size(); index++) {
                OrderItem item = orderItems.get(index);
                List<String> names = wrap(item.getProductName(), 270, 9, bold);
                String variant = "Size: " + safe(item.getSizeValue()) + " " + safe(item.getSizeUnit())
                        + "   SKU: " + safe(item.getSku());
                List<String> variants = wrap(variant, 270, 8, regular);
                float rowHeight = TABLE_ROW_PAD * 2 + names.size() * 12 + variants.size() * 11;
                if (y - rowHeight < BOTTOM) {
                    nextPage();
                    tableHeader();
                }
                if (index % 2 == 0) fillRect(MARGIN, y - rowHeight, CONTENT_W, rowHeight, LIGHT);
                float lineY = y - 15;
                for (String name : names) {
                    text(name, MARGIN + 11, lineY, 9, bold, DARK);
                    lineY -= 12;
                }
                for (String variantLine : variants) {
                    text(variantLine, MARGIN + 11, lineY, 8, regular, MUTED);
                    lineY -= 11;
                }
                float amountY = y - 22;
                rightText(safe(item.getQuantity()), MARGIN + 347, amountY, 9, regular, DARK);
                rightText("INR " + money(item.getUnitPrice()), MARGIN + 424, amountY, 9, regular, DARK);
                rightText("INR " + money(item.getLineTotal()), PAGE_W - MARGIN - 10,
                        amountY, 9, bold, DARK);
                y -= rowHeight;
            }
            y -= 20;
        }

        void drawTotals(Order order) throws IOException {
            ensure(163);
            float labelX = MARGIN + 273;
            float right = PAGE_W - MARGIN - 10;
            totalLine("Subtotal", money(order.getSubtotal()), labelX, right);
            totalLine("Delivery charge", money(order.getDeliveryCharge()), labelX, right);
            totalLine("Discount", "-" + money(order.getDiscountAmount()), labelX, right);
            totalLine("Tax recorded", money(order.getTaxAmount()), labelX, right);
            y -= 6;
            fillRect(MARGIN + 266, y - 40, CONTENT_W - 266, 46, GREEN);
            text("TOTAL", labelX + 8, y - 21, 11, bold, WHITE);
            rightText("INR " + money(order.getTotalAmount()), right, y - 21, 13, bold, WHITE);
            y -= 62;
        }

        private void totalLine(String label, String amount, float labelX, float right)
                throws IOException {
            text(label, labelX, y, 9, regular, MUTED);
            rightText("INR " + amount, right, y, 9, bold, DARK);
            y -= 20;
        }

        void drawClosingNote(Order order) throws IOException {
            ensure(78);
            rule(y, LINE);
            y -= 22;
            text("THANK YOU FOR YOUR ORDER!", MARGIN, y, 11, bold, GREEN);
            y -= 17;
            String note = "This document confirms your order only. It is not a GST tax invoice or a payment receipt.";
            y = wrappedText(note, MARGIN, y, CONTENT_W, 9, regular, MUTED, 13) - 8;
            if ("COD".equalsIgnoreCase(safe(order.getPaymentMethod())) &&
                    !"PAID".equalsIgnoreCase(safe(order.getPaymentStatus()))) {
                ensure(28);
                y = wrappedText("Cash on Delivery: payment is due upon delivery unless already collected.",
                        MARGIN, y, CONTENT_W, 9, regular, MUTED, 13);
            }
        }

        void drawPageFooters() throws IOException {
            int pages = doc.getNumberOfPages();
            for (int i = 0; i < pages; i++) {
                try (PDPageContentStream footer = new PDPageContentStream(doc,
                        doc.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.setStrokingColor(LINE[0], LINE[1], LINE[2]);
                    footer.setLineWidth(0.7f);
                    footer.moveTo(MARGIN, 49);
                    footer.lineTo(PAGE_W - MARGIN, 49);
                    footer.stroke();
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.setNonStrokingColor(MUTED[0], MUTED[1], MUTED[2]);
                    footer.newLineAtOffset(MARGIN, 34);
                    footer.showText("SHREEKRISHNA ORGANICS  |  Order confirmation");
                    footer.endText();
                    String pageLabel = "Page " + (i + 1) + " of " + pages;
                    footer.beginText();
                    footer.setFont(regular, 8);
                    footer.newLineAtOffset(PAGE_W - MARGIN - width(pageLabel, 8, regular), 34);
                    footer.showText(pageLabel);
                    footer.endText();
                }
            }
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}

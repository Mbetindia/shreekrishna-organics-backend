
        package com.shreekrishna.organics.order.service;

import com.shreekrishna.organics.order.entity.Order;
import com.shreekrishna.organics.order.entity.OrderItem;
import com.shreekrishna.organics.order.entity.PaymentMethod;
import com.shreekrishna.organics.order.entity.PaymentStatus;
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

    public OrderConfirmationPdfService(
            UserRepository users,
            OrderRepository orders,
            OrderItemRepository items
    ) {
        this.users = users;
        this.orders = orders;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public GeneratedInvoice generate(String email, Long orderId) {

        Long userId = users.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Customer not found"
                        )
                )
                .getId();

        Order order = orders.findByIdAndUserId(orderId, userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Order not found"
                        )
                );

        List<OrderItem> orderItems =
                items.findByOrder_IdOrderByIdAsc(orderId);

        OrderTaxView.Breakdown tax =
                OrderTaxView.from(order);

        System.out.println("========== INVOICE SERVICE DEBUG ==========");
        System.out.println("Order ID: " + orderId);
        System.out.println("Order Number: " + order.getOrderNumber());
        System.out.println("Payment Status: " + order.getPaymentStatus());
        System.out.println("Order Items Count: " + orderItems.size());
        System.out.println("Starting PDF generation...");

        try (
                PDDocument document = new PDDocument();
                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            Layout pdf = new Layout(document);

            try {
                System.out.println("Drawing header...");
                pdf.drawHeader(order);
                System.out.println("Drawing supplier...");
                pdf.drawSupplier();
                System.out.println("Drawing customer...");
                pdf.drawCustomer(order);
                System.out.println("Drawing items...");
                pdf.drawItems(orderItems);
                System.out.println("Drawing totals...");
                pdf.drawTotals(order, tax);
                System.out.println("Drawing closing note...");
                pdf.drawClosingNote(order);
                System.out.println("PDF content completed.");
            } finally {
                pdf.close();
            }

            System.out.println("Drawing page footers...");
            pdf.drawPageFooters();

            System.out.println("Saving PDF...");
            document.save(output);
            System.out.println("PDF saved. Bytes: " + output.size());

            String safeOrderNumber =
                    order.getOrderNumber() == null
                            ? String.valueOf(order.getId())
                            : order.getOrderNumber()
                            .replaceAll(
                                    "[^A-Za-z0-9._-]",
                                    "-"
                            );

            return new GeneratedInvoice(
                    output.toByteArray(),
                    "order-invoice-" +
                            safeOrderNumber +
                            ".pdf"
            );

        } catch (IOException | RuntimeException ex) {
            System.err.println("========== INVOICE SERVICE ERROR ==========");
            System.err.println("Order ID: " + orderId);
            System.err.println("Error Type: " + ex.getClass().getName());
            System.err.println("Error Message: " + ex.getMessage());
            ex.printStackTrace();

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to generate order invoice PDF",
                    ex
            );
        }
    }

    public record GeneratedInvoice(
            byte[] bytes,
            String filename
    ) {
    }

    private static String money(BigDecimal value) {

        return (
                value == null
                        ? BigDecimal.ZERO
                        : value
        )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                .toPlainString();
    }

    private static String rate(BigDecimal value) {

        return (
                value == null
                        ? BigDecimal.ZERO
                        : value
        )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String safe(Object value) {

        if (value == null) {
            return "-";
        }

        String text =
                value.toString().trim();

        if (text.isEmpty()) {
            return "-";
        }

        return text.replaceAll(
                "[^\\x20-\\x7E]",
                "?"
        );
    }

    private static final class Layout
            implements AutoCloseable {

        private static final float PAGE_W =
                PDRectangle.A4.getWidth();

        private static final float PAGE_H =
                PDRectangle.A4.getHeight();

        private static final float MARGIN = 43;

        private static final float CONTENT_W =
                PAGE_W - (2 * MARGIN);

        /*
         * Keep enough space for the footer.
         */
        private static final float BOTTOM = 68;

        /*
         * Slightly compact table rows.
         */
        private static final float TABLE_ROW_PAD = 8;

        private static final float[] GREEN = {
                0.16f,
                0.36f,
                0.25f
        };

        private static final float[] GOLD = {
                0.74f,
                0.55f,
                0.23f
        };

        private static final float[] DARK = {
                0.16f,
                0.19f,
                0.17f
        };

        private static final float[] MUTED = {
                0.43f,
                0.47f,
                0.44f
        };

        private static final float[] LIGHT = {
                0.95f,
                0.97f,
                0.94f
        };

        private static final float[] LINE = {
                0.85f,
                0.88f,
                0.85f
        };

        private static final float[] WHITE = {
                1f,
                1f,
                1f
        };

        /*
         * Supplier details taken from the GST
         * registration certificate.
         */
        private static final String LEGAL_NAME =
                "Pankaj Krishna Patil";

        private static final String TRADE_NAME =
                "Shreekrishna Organics";

        private static final String GSTIN =
                "27AMNPP2415M2ZL";

        private static final String SUPPLIER_ADDRESS_1 =
                "Ground Floor, M. No.3445/34, " +
                        "G.NO-P-1, Shri And Sou App";

        private static final String SUPPLIER_ADDRESS_2 =
                "Opposite Jivala Dhaba, " +
                        "Gajanan Society, Karad, Satara";

        private static final String SUPPLIER_ADDRESS_3 =
                "Maharashtra - 415124, India";

        private final PDDocument doc;

        private final PDFont regular =
                new PDType1Font(
                        Standard14Fonts.FontName.HELVETICA
                );

        private final PDFont bold =
                new PDType1Font(
                        Standard14Fonts.FontName.HELVETICA_BOLD
                );

        private PDPageContentStream stream;

        private float y;

        private int pageNumber;

        Layout(PDDocument doc)
                throws IOException {

            this.doc = doc;

            nextPage();
        }

        /*
         * --------------------------------------------------
         * Page handling
         * --------------------------------------------------
         */

        private void nextPage()
                throws IOException {

            if (stream != null) {
                stream.close();
            }

            PDPage page =
                    new PDPage(PDRectangle.A4);

            doc.addPage(page);

            stream =
                    new PDPageContentStream(
                            doc,
                            page
                    );

            pageNumber++;

            y = PAGE_H - 45;

            /*
             * Continuation pages use a compact header.
             */
            if (pageNumber > 1) {

                text(
                        "SHREEKRISHNA ORGANICS",
                        MARGIN,
                        y,
                        10,
                        bold,
                        GREEN
                );

                y -= 14;

                rule(y, LINE);

                y -= 20;
            }
        }

        private void ensure(float requiredHeight)
                throws IOException {

            if (y - requiredHeight < BOTTOM) {
                nextPage();
            }
        }

        /*
         * --------------------------------------------------
         * Basic drawing helpers
         * --------------------------------------------------
         */

        private void fillRect(
                float x,
                float bottomY,
                float width,
                float height,
                float[] color
        ) throws IOException {

            stream.setNonStrokingColor(
                    color[0],
                    color[1],
                    color[2]
            );

            stream.addRect(
                    x,
                    bottomY,
                    width,
                    height
            );

            stream.fill();
        }

        private void rule(
                float atY,
                float[] color
        ) throws IOException {

            stream.setStrokingColor(
                    color[0],
                    color[1],
                    color[2]
            );

            stream.setLineWidth(0.7f);

            stream.moveTo(
                    MARGIN,
                    atY
            );

            stream.lineTo(
                    PAGE_W - MARGIN,
                    atY
            );

            stream.stroke();
        }

        private float width(
                String value,
                float size,
                PDFont font
        ) throws IOException {

            return font.getStringWidth(
                    safe(value)
            ) * size / 1000f;
        }

        private void text(
                String value,
                float x,
                float baseline,
                float size,
                PDFont font,
                float[] color
        ) throws IOException {

            stream.beginText();

            stream.setNonStrokingColor(
                    color[0],
                    color[1],
                    color[2]
            );

            stream.setFont(
                    font,
                    size
            );

            stream.newLineAtOffset(
                    x,
                    baseline
            );

            stream.showText(
                    safe(value)
            );

            stream.endText();
        }

        private void rightText(
                String value,
                float right,
                float baseline,
                float size,
                PDFont font,
                float[] color
        ) throws IOException {

            text(
                    value,
                    right - width(
                            value,
                            size,
                            font
                    ),
                    baseline,
                    size,
                    font,
                    color
            );
        }

        /*
         * --------------------------------------------------
         * Text wrapping
         * --------------------------------------------------
         */

        private List<String> wrap(
                String value,
                float maxWidth,
                float size,
                PDFont font
        ) throws IOException {

            String remaining =
                    safe(value);

            List<String> lines =
                    new ArrayList<>();

            while (!remaining.isEmpty()) {

                if (
                        width(
                                remaining,
                                size,
                                font
                        ) <= maxWidth
                ) {

                    lines.add(remaining);

                    break;
                }

                int cut =
                        remaining.length();

                while (
                        cut > 1 &&
                                width(
                                        remaining.substring(
                                                0,
                                                cut
                                        ),
                                        size,
                                        font
                                ) > maxWidth
                ) {
                    cut--;
                }

                int space =
                        remaining.lastIndexOf(
                                ' ',
                                cut
                        );

                if (space > 0) {
                    cut = space;
                }

                lines.add(
                        remaining.substring(
                                0,
                                cut
                        ).strip()
                );

                remaining =
                        remaining.substring(
                                cut
                        ).stripLeading();
            }

            if (lines.isEmpty()) {
                lines.add("-");
            }

            return lines;
        }

        private float wrappedHeight(
                String value,
                float maxWidth,
                float size,
                PDFont font,
                float leading
        ) throws IOException {

            return wrap(
                    value,
                    maxWidth,
                    size,
                    font
            ).size() * leading;
        }

        private float wrappedText(
                String value,
                float x,
                float topBaseline,
                float maxWidth,
                float size,
                PDFont font,
                float[] color,
                float leading
        ) throws IOException {

            float baseline =
                    topBaseline;

            for (
                    String line :
                    wrap(
                            value,
                            maxWidth,
                            size,
                            font
                    )
            ) {

                text(
                        line,
                        x,
                        baseline,
                        size,
                        font,
                        color
                );

                baseline -= leading;
            }

            return baseline;
        }

        /*
         * --------------------------------------------------
         * Main invoice header
         * --------------------------------------------------
         */

        void drawHeader(Order order)
                throws IOException {

            fillRect(
                    0,
                    PAGE_H - 125,
                    PAGE_W,
                    125,
                    GREEN
            );

            fillRect(
                    MARGIN,
                    PAGE_H - 129,
                    95,
                    4,
                    GOLD
            );

            text(
                    "SHREEKRISHNA",
                    MARGIN,
                    PAGE_H - 56,
                    20,
                    bold,
                    WHITE
            );

            text(
                    "ORGANICS",
                    MARGIN,
                    PAGE_H - 78,
                    16,
                    bold,
                    WHITE
            );

            text(
                    "Connect with nature and boost your health",
                    MARGIN,
                    PAGE_H - 98,
                    8,
                    regular,
                    WHITE
            );

            text(
                    "GST Reg. No.: " + GSTIN,
                    MARGIN,
                    PAGE_H - 114,
                    8,
                    bold,
                    WHITE
            );

            if (
                    order.getPaymentStatus()
                            == PaymentStatus.PAID
            ) {

                rightText(
                        "PAID ORDER",
                        PAGE_W - MARGIN,
                        PAGE_H - 59,
                        17,
                        bold,
                        WHITE
                );

                rightText(
                        "INVOICE",
                        PAGE_W - MARGIN,
                        PAGE_H - 78,
                        11,
                        bold,
                        WHITE
                );

                rightText(
                        "PAYMENT RECEIVED",
                        PAGE_W - MARGIN,
                        PAGE_H - 97,
                        8,
                        regular,
                        WHITE
                );

            } else {

                rightText(
                        "ORDER",
                        PAGE_W - MARGIN,
                        PAGE_H - 59,
                        17,
                        bold,
                        WHITE
                );

                rightText(
                        "INVOICE",
                        PAGE_W - MARGIN,
                        PAGE_H - 78,
                        11,
                        bold,
                        WHITE
                );

                rightText(
                        "PAYMENT DUE / NOT A RECEIPT",
                        PAGE_W - MARGIN,
                        PAGE_H - 97,
                        8,
                        regular,
                        WHITE
                );
            }

            y = PAGE_H - 151;

            text(
                    "ORDER REFERENCE",
                    MARGIN,
                    y,
                    8,
                    bold,
                    MUTED
            );

            text(
                    "ORDER DATE",
                    MARGIN + 260,
                    y,
                    8,
                    bold,
                    MUTED
            );

            y -= 16;

            float leftY =
                    wrappedText(
                            order.getOrderNumber(),
                            MARGIN,
                            y,
                            250,
                            10,
                            bold,
                            DARK,
                            13
                    );

            String date =
                    order.getCreatedAt() == null
                            ? "Pending"
                            : order
                            .getCreatedAt()
                            .format(
                                    DateTimeFormatter
                                            .ofPattern(
                                                    "dd MMM yyyy, hh:mm a"
                                            )
                            );

            text(
                    date,
                    MARGIN + 260,
                    y,
                    10,
                    regular,
                    DARK
            );

            y = Math.min(
                    leftY,
                    y - 14
            ) - 6;

            rule(y, LINE);

            y -= 16;
        }

        /*
         * --------------------------------------------------
         * Supplier details
         * --------------------------------------------------
         */

        void drawSupplier()
                throws IOException {

            ensure(86);

            text(
                    "SUPPLIER",
                    MARGIN,
                    y,
                    10,
                    bold,
                    GREEN
            );

            text(
                    "GST REGISTRATION",
                    MARGIN + 282,
                    y,
                    10,
                    bold,
                    GREEN
            );

            y -= 17;

            float leftY = y;

            leftY =
                    wrappedText(
                            TRADE_NAME,
                            MARGIN,
                            leftY,
                            250,
                            10,
                            bold,
                            DARK,
                            13
                    );

            leftY =
                    wrappedText(
                            "Legal name: " +
                                    LEGAL_NAME,
                            MARGIN,
                            leftY,
                            250,
                            8,
                            regular,
                            MUTED,
                            11
                    );

            leftY =
                    wrappedText(
                            SUPPLIER_ADDRESS_1,
                            MARGIN,
                            leftY,
                            250,
                            8,
                            regular,
                            DARK,
                            10
                    );

            leftY =
                    wrappedText(
                            SUPPLIER_ADDRESS_2,
                            MARGIN,
                            leftY,
                            250,
                            8,
                            regular,
                            DARK,
                            10
                    );

            leftY =
                    wrappedText(
                            SUPPLIER_ADDRESS_3,
                            MARGIN,
                            leftY,
                            250,
                            8,
                            regular,
                            DARK,
                            10
                    );

            float rightY = y;

            rightY =
                    detail(
                            "GSTIN",
                            GSTIN,
                            rightY
                    );

            rightY =
                    detail(
                            "Registration",
                            "Regular",
                            rightY
                    );

            rightY =
                    detail(
                            "Supplier State",
                            "Maharashtra",
                            rightY
                    );

            y = Math.min(
                    leftY,
                    rightY
            ) - 8;

            rule(y, LINE);

            y -= 16;
        }

        /*
         * --------------------------------------------------
         * Customer / payment details
         * --------------------------------------------------
         */

        void drawCustomer(Order order)
                throws IOException {

            ensure(125);

            text(
                    "DELIVERY DETAILS",
                    MARGIN,
                    y,
                    10,
                    bold,
                    GREEN
            );

            text(
                    "ORDER & PAYMENT",
                    MARGIN + 282,
                    y,
                    10,
                    bold,
                    GREEN
            );

            y -= 18;

            float leftY = y;

            leftY =
                    wrappedText(
                            order.getShippingFullName(),
                            MARGIN,
                            leftY,
                            245,
                            10,
                            bold,
                            DARK,
                            13
                    );

            leftY =
                    wrappedText(
                            order.getShippingMobile(),
                            MARGIN,
                            leftY,
                            245,
                            9,
                            regular,
                            MUTED,
                            12
                    );

            leftY =
                    wrappedText(
                            order.getShippingAddressLine1(),
                            MARGIN,
                            leftY,
                            245,
                            9,
                            regular,
                            DARK,
                            12
                    );

            if (
                    hasText(
                            order.getShippingAddressLine2()
                    )
            ) {

                leftY =
                        wrappedText(
                                order.getShippingAddressLine2(),
                                MARGIN,
                                leftY,
                                245,
                                9,
                                regular,
                                DARK,
                                12
                        );
            }

            if (
                    hasText(
                            order.getShippingLandmark()
                    )
            ) {

                leftY =
                        wrappedText(
                                order.getShippingLandmark(),
                                MARGIN,
                                leftY,
                                245,
                                9,
                                regular,
                                DARK,
                                12
                        );
            }

            leftY =
                    wrappedText(
                            safe(
                                    order.getShippingCity()
                            ) +
                                    ", " +
                                    safe(
                                            order.getShippingState()
                                    ) +
                                    " - " +
                                    safe(
                                            order.getShippingPostalCode()
                                    ),
                            MARGIN,
                            leftY,
                            245,
                            9,
                            regular,
                            DARK,
                            12
                    );

            leftY =
                    wrappedText(
                            order.getShippingCountry(),
                            MARGIN,
                            leftY,
                            245,
                            9,
                            regular,
                            DARK,
                            12
                    );

            float rightY = y;

            rightY =
                    detail(
                            "Order status",
                            safe(
                                    order.getStatus()
                            ),
                            rightY
                    );

            rightY =
                    detail(
                            "Payment method",
                            safe(
                                    order.getPaymentMethod()
                            ),
                            rightY
                    );

            rightY =
                    detail(
                            "Payment status",
                            safe(
                                    order.getPaymentStatus()
                            ),
                            rightY
                    );

            rightY =
                    detail(
                            "Place of supply",
                            safe(
                                    order.getShippingState()
                            ),
                            rightY
                    );

            y = Math.min(
                    leftY,
                    rightY
            ) - 11;

            rule(y, LINE);

            y -= 20;
        }

        private float detail(
                String label,
                String value,
                float baseline
        ) throws IOException {

            text(
                    label,
                    MARGIN + 282,
                    baseline,
                    9,
                    regular,
                    MUTED
            );

            rightText(
                    value,
                    PAGE_W - MARGIN,
                    baseline,
                    9,
                    bold,
                    DARK
            );

            return baseline - 18;
        }

        /*
         * --------------------------------------------------
         * Order items
         * --------------------------------------------------
         */

        private void tableHeader()
                throws IOException {

            ensure(32);

            fillRect(
                    MARGIN,
                    y - 25,
                    CONTENT_W,
                    29,
                    GREEN
            );

            text(
                    "PRODUCT / VARIANT",
                    MARGIN + 11,
                    y - 15,
                    9,
                    bold,
                    WHITE
            );

            rightText(
                    "QTY",
                    MARGIN + 347,
                    y - 15,
                    9,
                    bold,
                    WHITE
            );

            rightText(
                    "RATE",
                    MARGIN + 424,
                    y - 15,
                    9,
                    bold,
                    WHITE
            );

            rightText(
                    "AMOUNT",
                    PAGE_W - MARGIN - 10,
                    y - 15,
                    9,
                    bold,
                    WHITE
            );

            y -= 29;
        }

        void drawItems(
                List<OrderItem> orderItems
        ) throws IOException {

            ensure(45);

            text(
                    "ORDER ITEMS",
                    MARGIN,
                    y,
                    11,
                    bold,
                    GREEN
            );

            y -= 17;

            tableHeader();

            if (orderItems.isEmpty()) {

                text(
                        "No items found",
                        MARGIN + 10,
                        y - 18,
                        9,
                        regular,
                        MUTED
                );

                y -= 38;

                return;
            }

            for (
                    int index = 0;
                    index < orderItems.size();
                    index++
            ) {

                OrderItem item =
                        orderItems.get(index);

                List<String> names =
                        wrap(
                                item.getProductName(),
                                270,
                                9,
                                bold
                        );

                String variant =
                        "Size: " +
                                safe(
                                        item.getSizeValue()
                                ) +
                                " " +
                                safe(
                                        item.getSizeUnit()
                                ) +
                                "   SKU: " +
                                safe(
                                        item.getSku()
                                );

                List<String> variants =
                        wrap(
                                variant,
                                270,
                                8,
                                regular
                        );

                float rowHeight =
                        TABLE_ROW_PAD * 2
                                + names.size() * 12
                                + variants.size() * 10;

                /*
                 * If this item cannot fit on the
                 * current page, continue on a new page.
                 */
                if (
                        y - rowHeight < BOTTOM
                ) {

                    nextPage();

                    text(
                            "ORDER ITEMS - CONTINUED",
                            MARGIN,
                            y,
                            10,
                            bold,
                            GREEN
                    );

                    y -= 17;

                    tableHeader();
                }

                if (index % 2 == 0) {

                    fillRect(
                            MARGIN,
                            y - rowHeight,
                            CONTENT_W,
                            rowHeight,
                            LIGHT
                    );
                }

                float lineY =
                        y - 14;

                for (
                        String name :
                        names
                ) {

                    text(
                            name,
                            MARGIN + 11,
                            lineY,
                            9,
                            bold,
                            DARK
                    );

                    lineY -= 12;
                }

                for (
                        String variantLine :
                        variants
                ) {

                    text(
                            variantLine,
                            MARGIN + 11,
                            lineY,
                            8,
                            regular,
                            MUTED
                    );

                    lineY -= 10;
                }

                float amountY =
                        y - 20;

                rightText(
                        safe(
                                item.getQuantity()
                        ),
                        MARGIN + 347,
                        amountY,
                        9,
                        regular,
                        DARK
                );

                rightText(
                        "INR " +
                                money(
                                        item.getUnitPrice()
                                ),
                        MARGIN + 424,
                        amountY,
                        9,
                        regular,
                        DARK
                );

                rightText(
                        "INR " +
                                money(
                                        item.getLineTotal()
                                ),
                        PAGE_W - MARGIN - 10,
                        amountY,
                        9,
                        bold,
                        DARK
                );

                y -= rowHeight;
            }

            /*
             * Small spacing only.
             * Previously this gap was larger and
             * contributed to unnecessary page breaks.
             */
            y -= 13;
        }

        /*
         * --------------------------------------------------
         * Dynamic totals
         * --------------------------------------------------
         */

        private int totalLineCount(
                Order order,
                OrderTaxView.Breakdown tax
        ) {

            int lines = 1;

            /*
             * GST section.
             */
            if (
                    tax.cgstAmount().signum() > 0 ||
                            tax.sgstAmount().signum() > 0
            ) {

                lines += 2;

            } else {

                /*
                 * Either IGST or zero GST.
                 */
                lines += 1;
            }

            /*
             * Product subtotal + delivery.
             */
            lines += 2;

            if (
                    order.getCodCharge() != null &&
                            order.getCodCharge()
                                    .signum() > 0
            ) {

                lines++;
            }

            if (
                    order.getDiscountAmount() != null &&
                            order.getDiscountAmount()
                                    .signum() > 0
            ) {

                lines++;
            }

            return lines;
        }

        private float totalsRequiredHeight(
                Order order,
                OrderTaxView.Breakdown tax
        ) {

            /*
             * Each standard total row uses 18 pt.
             * The green total block plus margins needs
             * roughly 61 pt.
             */
            return (
                    totalLineCount(
                            order,
                            tax
                    ) * 18f
            ) + 68f;
        }

        void drawTotals(
                Order order,
                OrderTaxView.Breakdown tax
        ) throws IOException {

            /*
             * Important:
             * Do NOT reserve a fixed 230 pt block.
             * Reserve only what this order actually needs.
             */
            ensure(
                    totalsRequiredHeight(
                            order,
                            tax
                    )
            );

            float labelX =
                    MARGIN + 245;

            float right =
                    PAGE_W - MARGIN - 10;

            String productLabel =
                    tax.gstIncludedInSubtotal()
                            ? "Product Price (Excluding GST)"
                            : "Product Price (Excluding GST)";

            totalLine(
                    productLabel,
                    money(
                            tax.taxableAmount()
                    ),
                    labelX,
                    right
            );

            if (
                    tax.cgstAmount().signum() > 0 ||
                            tax.sgstAmount().signum() > 0
            ) {

                totalLine(
                        "CGST (" +
                                rate(
                                        tax.cgstRate()
                                ) +
                                "%)" + (tax.gstIncludedInSubtotal() ? " - Included" : " - Added"),
                        money(
                                tax.cgstAmount()
                        ),
                        labelX,
                        right
                );

                totalLine(
                        "SGST (" +
                                rate(
                                        tax.sgstRate()
                                ) +
                                "%)" + (tax.gstIncludedInSubtotal() ? " - Included" : " - Added"),
                        money(
                                tax.sgstAmount()
                        ),
                        labelX,
                        right
                );

            } else if (
                    tax.igstAmount().signum() > 0
            ) {

                totalLine(
                        "IGST (" +
                                rate(
                                        tax.igstRate()
                                ) +
                                "%)" + (tax.gstIncludedInSubtotal() ? " - Included" : " - Added"),
                        money(
                                tax.igstAmount()
                        ),
                        labelX,
                        right
                );

            } else {

                totalLine(
                        "GST",
                        "0.00",
                        labelX,
                        right
                );
            }

            totalLine(
                    tax.gstIncludedInSubtotal()
                            ? "Product Price (Including GST)"
                            : "Product Price (Excluding GST)",
                    money(
                            order.getSubtotal()
                    ),
                    labelX,
                    right
            );

            totalLine(
                    "Delivery Charge",
                    money(
                            order.getDeliveryCharge()
                    ),
                    labelX,
                    right
            );

            if (
                    order.getCodCharge() != null &&
                            order.getCodCharge()
                                    .signum() > 0
            ) {

                totalLine(
                        "COD Charge",
                        money(
                                order.getCodCharge()
                        ),
                        labelX,
                        right
                );
            }

            if (
                    order.getDiscountAmount() != null &&
                            order.getDiscountAmount()
                                    .signum() > 0
            ) {

                totalLine(
                        "Discount",
                        "-" +
                                money(
                                        order.getDiscountAmount()
                                ),
                        labelX,
                        right
                );
            }

            y -= 5;

            /*
             * Keep TOTAL PAYABLE as one block.
             */
            fillRect(
                    MARGIN + 238,
                    y - 37,
                    CONTENT_W - 238,
                    43,
                    GREEN
            );

            text(
                    "TOTAL PAYABLE",
                    labelX,
                    y - 20,
                    11,
                    bold,
                    WHITE
            );

            rightText(
                    "INR " +
                            money(
                                    order.getTotalAmount()
                            ),
                    right,
                    y - 20,
                    13,
                    bold,
                    WHITE
            );

            y -= 54;
        }

        private void totalLine(
                String label,
                String amount,
                float labelX,
                float right
        ) throws IOException {

            text(
                    label,
                    labelX,
                    y,
                    9,
                    regular,
                    MUTED
            );

            rightText(
                    "INR " + amount,
                    right,
                    y,
                    9,
                    bold,
                    DARK
            );

            y -= 18;
        }

        /*
         * --------------------------------------------------
         * Customer-facing closing notes
         * --------------------------------------------------
         */

        private String paymentNote(
                Order order
        ) {

            if (
                    order.getPaymentMethod()
                            == PaymentMethod.RAZORPAY &&
                            order.getPaymentStatus()
                                    == PaymentStatus.PAID
            ) {

                return "Payment received successfully via Razorpay.";
            }

            if (
                    order.getPaymentMethod()
                            == PaymentMethod.COD &&
                            order.getPaymentStatus()
                                    != PaymentStatus.PAID
            ) {

                return "Cash on Delivery payment is still due. " +
                        "This document is not proof of payment.";
            }

            if (
                    order.getPaymentStatus()
                            == PaymentStatus.PAID
            ) {

                return "Payment received successfully for this order.";
            }

            return "Payment is pending for this order.";
        }

        private String gstCustomerNote(
                Order order
        ) {

            if (
                    order.getPaymentStatus()
                            == PaymentStatus.PAID
            ) {

                return "GST is shown as included or added according to the order. " +
                        "This document records your order and payment.";
            }

            return "GST is shown as included or added according to the order. " +
                    "This document records your order.";
        }

        private float closingNoteRequiredHeight(
                Order order
        ) throws IOException {

            String payment =
                    paymentNote(order);

            String gst =
                    gstCustomerNote(order);

            float paymentHeight =
                    wrappedHeight(
                            payment,
                            CONTENT_W,
                            9,
                            regular,
                            12
                    );

            float gstHeight =
                    wrappedHeight(
                            gst,
                            CONTENT_W,
                            8,
                            regular,
                            10
                    );

            /*
             * Rule + title + gaps + both notes.
             */
            return 47
                    + paymentHeight
                    + gstHeight;
        }

        void drawClosingNote(
                Order order
        ) throws IOException {

            /*
             * Only move to a new page when the actual
             * closing content does not fit.
             */
            ensure(
                    closingNoteRequiredHeight(
                            order
                    )
            );

            rule(y, LINE);

            y -= 17;

            text(
                    "THANK YOU FOR YOUR ORDER!",
                    MARGIN,
                    y,
                    11,
                    bold,
                    GREEN
            );

            y -= 16;

            y =
                    wrappedText(
                            paymentNote(order),
                            MARGIN,
                            y,
                            CONTENT_W,
                            9,
                            regular,
                            MUTED,
                            12
                    ) - 5;

            y =
                    wrappedText(
                            gstCustomerNote(order),
                            MARGIN,
                            y,
                            CONTENT_W,
                            8,
                            regular,
                            MUTED,
                            10
                    );
        }

        /*
         * --------------------------------------------------
         * Page footers
         * --------------------------------------------------
         */

        void drawPageFooters()
                throws IOException {

            int pages =
                    doc.getNumberOfPages();

            for (
                    int i = 0;
                    i < pages;
                    i++
            ) {

                try (
                        PDPageContentStream footer =
                                new PDPageContentStream(
                                        doc,
                                        doc.getPage(i),
                                        PDPageContentStream
                                                .AppendMode.APPEND,
                                        true,
                                        true
                                )
                ) {

                    footer.setStrokingColor(
                            LINE[0],
                            LINE[1],
                            LINE[2]
                    );

                    footer.setLineWidth(0.7f);

                    footer.moveTo(
                            MARGIN,
                            49
                    );

                    footer.lineTo(
                            PAGE_W - MARGIN,
                            49
                    );

                    footer.stroke();

                    footer.beginText();

                    footer.setFont(
                            regular,
                            8
                    );

                    footer.setNonStrokingColor(
                            MUTED[0],
                            MUTED[1],
                            MUTED[2]
                    );

                    footer.newLineAtOffset(
                            MARGIN,
                            34
                    );

                    footer.showText(
                            "SHREEKRISHNA ORGANICS  |  Order invoice"
                    );

                    footer.endText();

                    String pageLabel =
                            "Page " +
                                    (i + 1) +
                                    " of " +
                                    pages;

                    footer.beginText();

                    footer.setFont(
                            regular,
                            8
                    );

                    footer.newLineAtOffset(
                            PAGE_W -
                                    MARGIN -
                                    width(
                                            pageLabel,
                                            8,
                                            regular
                                    ),
                            34
                    );

                    footer.showText(
                            pageLabel
                    );

                    footer.endText();
                }
            }
        }

        private static boolean hasText(
                String value
        ) {

            return value != null &&
                    !value.isBlank();
        }

        @Override
        public void close()
                throws IOException {

            if (stream != null) {

                stream.close();

                stream = null;
            }
        }
    }
}
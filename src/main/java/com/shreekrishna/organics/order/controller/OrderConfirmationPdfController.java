
package com.shreekrishna.organics.order.controller;

import com.shreekrishna.organics.order.service.OrderConfirmationPdfService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderConfirmationPdfController {

    private final OrderConfirmationPdfService pdfService;

    public OrderConfirmationPdfController(
            OrderConfirmationPdfService pdfService
    ) {
        this.pdfService = pdfService;
    }

    @GetMapping(
            value = "/{id}/invoice",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            Authentication authentication
    ) {
        System.out.println("========== INVOICE CONTROLLER ==========");
        System.out.println("Order ID: " + id);

        try {
            String email = authentication.getName();

            System.out.println("Calling PDF Service...");

            OrderConfirmationPdfService.GeneratedInvoice generated =
                    pdfService.generate(email, id);

            System.out.println("PDF generated successfully!");
            System.out.println("PDF Size: " + generated.bytes().length);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename(generated.filename())
                                    .build()
                                    .toString()
                    )
                    .contentLength(generated.bytes().length)
                    .body(generated.bytes());

        } catch (Exception ex) {
            System.err.println(
                    "========== INVOICE CONTROLLER ERROR =========="
            );
            System.err.println("Order ID: " + id);
            System.err.println("Error Type: " + ex.getClass().getName());
            System.err.println("Error Message: " + ex.getMessage());

            ex.printStackTrace();
            throw ex;
        }
    }
}

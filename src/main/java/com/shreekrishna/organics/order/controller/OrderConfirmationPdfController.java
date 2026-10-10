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

    public OrderConfirmationPdfController(OrderConfirmationPdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GetMapping(value = "/{id}/invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable Long id, Authentication authentication) {
        byte[] pdf = pdfService.generate(authentication.getName(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("order-confirmation-" + id + ".pdf")
                                .build().toString())
                .contentLength(pdf.length)
                .body(pdf);
    }
}

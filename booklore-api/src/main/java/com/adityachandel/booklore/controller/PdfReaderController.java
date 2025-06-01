package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.CbxReaderService;
import com.adityachandel.booklore.service.PdfReaderService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pdf")
@RequiredArgsConstructor
public class PdfReaderController {

    private final PdfReaderService pdfReaderService;

    @GetMapping("/{bookId}/pages")
    public List<Integer> listPages(@PathVariable Long bookId) throws IOException {
        return pdfReaderService.getAvailablePages(bookId);
    }

    @GetMapping("/{bookId}/pages/{pageNumber}")
    public void getPage(@PathVariable Long bookId, @PathVariable int pageNumber, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        pdfReaderService.streamPageImage(bookId, pageNumber, response.getOutputStream());
    }
}
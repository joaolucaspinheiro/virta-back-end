package br.com.virta.backend.controller;

import br.com.virta.backend.model.TransactionType;
import br.com.virta.backend.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets/{walletId}/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * CSV statement. Example:
     * /reports/statement?from=2026-09&to=2026-11&categoryIds=3&categoryIds=7&type=EXPENSE
     */
    @GetMapping("/statement")
    public ResponseEntity<byte[]> statement(@PathVariable Long walletId,
                                            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
                                            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth to,
                                            @RequestParam(required = false) List<Long> categoryIds,
                                            @RequestParam(required = false) TransactionType type,
                                            Authentication auth) {
        String csv = reportService.statementCsv(auth.getName(), walletId, from, to, categoryIds, type);

        // BOM: makes Excel read the file as UTF-8 (correct accents).
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        String filename = from.equals(to)
                ? "extrato-" + from + ".csv"
                : "extrato-" + from + "_a_" + to + ".csv";

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}

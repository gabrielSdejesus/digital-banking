package com.corebank.digital_banking.application.transaction;

import com.corebank.digital_banking.domain.transaction.Transfer;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PdfGenerator {

    private PdfGenerator() {
    }

    public static byte[] generateStatementPdf(String accountId, int year, List<Transfer> transfers, Map<UUID, String> accountNames) {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Font styles
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.GRAY);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

            // Document Header
            Paragraph title = new Paragraph("Digital Banking - Account Statement", titleFont);
            title.setSpacingAfter(5);
            document.add(title);

            Paragraph subtitle = new Paragraph("Account ID: " + accountId + " | Year: " + year, subTitleFont);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // Table setup
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2.5f, 2.5f, 1.5f, 2f});

            // Headers
            String[] headers = {"Source Account", "Destination Account", "Amount", "Timestamp"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
                cell.setBackgroundColor(Color.BLUE);
                cell.setPadding(6);
                table.addCell(cell);
            }

            // Create formatter dynamically with UTC fuso
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                    .withZone(java.time.ZoneOffset.UTC);

            // Body
            for (Transfer transfer : transfers) {
                String sourceName = accountNames.getOrDefault(transfer.getSourceAccountId(), transfer.getSourceAccountId().toString());
                String destName = accountNames.getOrDefault(transfer.getDestinationAccountId(), transfer.getDestinationAccountId().toString());

                table.addCell(new PdfPCell(new Paragraph(sourceName, bodyFont)));
                table.addCell(new PdfPCell(new Paragraph(destName, bodyFont)));
                table.addCell(new PdfPCell(new Paragraph("$" + transfer.getAmount().setScale(4, RoundingMode.HALF_DOWN).toString(), bodyFont)));
                table.addCell(new PdfPCell(new Paragraph(formatter.format(transfer.getCreatedAt()), bodyFont)));
            }

            document.add(table);
            document.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Error occurred while generating PDF", e);
        }

        return out.toByteArray();
    }
}

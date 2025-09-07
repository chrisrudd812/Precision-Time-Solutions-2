package timeclock.util;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

public class PdfGenerator {

    public static byte[] createTimecardPdf(Map<String, Object> cardData, String payPeriodMessage) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.A4);
        document.setMargins(36, 36, 36, 36);

        // Header
        document.add(new Paragraph((String) cardData.getOrDefault("employeeName", "N/A"))
            .setBold().setFontSize(18).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph(payPeriodMessage)
            .setFontSize(12).setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

        // Employee Info Table
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2, 1, 2}));
        infoTable.setWidth(UnitValue.createPercentValue(100)).setMarginBottom(20);
        addInfoCell(infoTable, "Department:", (String) cardData.getOrDefault("department", "N/A"));
        addInfoCell(infoTable, "Schedule:", (String) cardData.getOrDefault("scheduleName", "N/A"));
        addInfoCell(infoTable, "Supervisor:", (String) cardData.getOrDefault("supervisor", "N/A"));
        addInfoCell(infoTable, "Auto Lunch:", (String) cardData.getOrDefault("autoLunchStr", "Off"));
        document.add(infoTable);

        // Punches Table
        Table punchesTable = new Table(UnitValue.createPercentArray(new float[]{1.5f, 2, 2, 2, 1.5f, 2}));
        punchesTable.setWidth(UnitValue.createPercentValue(100)).setMarginBottom(20);
        addHeaderCell(punchesTable, "Day");
        addHeaderCell(punchesTable, "Date");
        addHeaderCell(punchesTable, "IN");
        addHeaderCell(punchesTable, "OUT");
        addHeaderCell(punchesTable, "Total Hours");
        addHeaderCell(punchesTable, "Punch Type");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> punchesList = (List<Map<String, String>>) cardData.get("punchesList");
        if (punchesList != null && !punchesList.isEmpty()) {
            for (Map<String, String> punch : punchesList) {
                punchesTable.addCell(new Cell().add(new Paragraph(punch.getOrDefault("dayOfWeek", ""))));
                punchesTable.addCell(new Cell().add(new Paragraph(punch.getOrDefault("friendlyPunchDate", ""))));
                punchesTable.addCell(new Cell().add(new Paragraph(punch.getOrDefault("timeIn", ""))));
                punchesTable.addCell(new Cell().add(new Paragraph(punch.getOrDefault("timeOut", ""))));
                punchesTable.addCell(new Cell().add(new Paragraph(punch.getOrDefault("totalHours", ""))).setTextAlignment(TextAlignment.RIGHT));
                punchesTable.addCell(new Cell().add(new Paragraph(punch.getOrDefault("punchType", ""))));
            }
        } else {
            punchesTable.addCell(new Cell(1, 6).add(new Paragraph("No punch data for this period.")).setTextAlignment(TextAlignment.CENTER));
        }
        document.add(punchesTable);

        // Totals Footer Table
        Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}));
        totalsTable.setWidth(UnitValue.createPercentValue(60)).setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT);
        addTotalsCell(totalsTable, "Regular Hours:", String.format("%.2f", cardData.getOrDefault("totalRegularHours", 0.0)), false);
        addTotalsCell(totalsTable, "Overtime Hours:", String.format("%.2f", cardData.getOrDefault("totalOvertimeHours", 0.0)), false);
        addTotalsCell(totalsTable, "Double Time Hours:", String.format("%.2f", cardData.getOrDefault("totalDoubleTimeHours", 0.0)), false);
        addTotalsCell(totalsTable, "Total Paid Hours:", String.format("%.2f", cardData.getOrDefault("periodTotalHours", 0.0)), true);
        document.add(totalsTable);
        
        document.close();
        return baos.toByteArray();
    }

    private static void addInfoCell(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold()).setBorder(null));
        table.addCell(new Cell().add(new Paragraph(value)).setBorder(null));
    }

    private static void addHeaderCell(Table table, String text) {
        table.addHeaderCell(new Cell().add(new Paragraph(text)).setBackgroundColor(ColorConstants.LIGHT_GRAY).setBold());
    }
    
    private static void addTotalsCell(Table table, String label, String value, boolean isBold) {
        Paragraph labelParagraph = new Paragraph(label);
        Paragraph valueParagraph = new Paragraph(value);
        if (isBold) {
            labelParagraph.setBold();
            valueParagraph.setBold();
        }
        table.addCell(new Cell().add(labelParagraph).setTextAlignment(TextAlignment.RIGHT).setBorder(null));
        table.addCell(new Cell().add(valueParagraph).setTextAlignment(TextAlignment.RIGHT).setBorder(null));
    }
}
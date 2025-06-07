package se.timotej.wr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import se.timotej.wr.model.Heat;
import se.timotej.wr.model.Omgang;
import se.timotej.wr.model.Race;
import se.timotej.wr.model.Start;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.trimToNull;

public class WRResultGenerator {
    private static final String RESULT_HTML = "result.html";
    private static final String RESULT_JSON = "result.json";
    private final String hanFil;
    private final String tikFil;
    private final String sektion;
    private final String datum;
    private FileTime hanModifiedTime;
    private FileTime tikModifiedTime;
    private volatile boolean canceled = false;
    private final Set<String> licensHundNamn = new HashSet<>();
    private final Map<String, String> hundNamnsKon = new HashMap<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: WRResultGenerator <hanfil> <tikfil> <monitor true/false>");
            System.exit(1);
        }
        String hanFil = args[0];
        String tikFil = args[1];
        boolean monitor = Boolean.parseBoolean(args[2]);
        WRResultGenerator wrResultGenerator = new WRResultGenerator(hanFil, tikFil, "Test", "2099-12-31");
        wrResultGenerator.run();
        if (monitor) {
            wrResultGenerator.monitor(null);
        }
        System.exit(0);
    }

    public void monitor(Runnable resultUploadedCallback) throws IOException {
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (canceled) {
                return;
            }
            if (!hanModifiedTime.equals(Files.getLastModifiedTime(Path.of(hanFil)))
                    || !tikModifiedTime.equals(Files.getLastModifiedTime(Path.of(tikFil)))) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                run();
                if (resultUploadedCallback != null) {
                    resultUploadedCallback.run();
                }
            }
        }
    }

    public WRResultGenerator(String hanFil, String tikFil, String sektion, String datum) {
        this.hanFil = hanFil;
        this.tikFil = tikFil;
        this.sektion = sektion;
        this.datum = datum;
    }

    public void run() throws IOException {
        System.out.println("WRResultGenerator.run");
        hanModifiedTime = Files.getLastModifiedTime(Path.of(hanFil));
        tikModifiedTime = Files.getLastModifiedTime(Path.of(tikFil));

        PrintStream out = new PrintStream(RESULT_HTML);
        out.println("<html>");
        out.println("<head>");
        out.println("<meta charset=\"utf-8\" />");
        out.println("<link rel=\"stylesheet\" href=\"style.css\">");
        out.println("<script>");
        out.println("    function reload() {");
        out.println("        const url = new URL(window.location);");
        out.println("        url.searchParams.set('a', Math.random());");
        out.println("        location.href = url.toString();");
        out.println("    }");
        out.println("</script>");
        out.printf("<title>%s %s</title>%n", sektion, datum);
        out.println("</head>");
        out.println("<body>");
        out.printf("<h1>%s %s</h1>%n", sektion, datum);
        out.println("<p class='excludePrint'>Preliminära resultat<br>");
        out.println("<button onClick=\"reload()\">Ladda om</button></p>");

        List<Omgang> omgangar = new ArrayList<>();

        Workbook hanarWorkbook = WorkbookFactory.create(new FileInputStream(hanFil));
        if (StringUtils.isNotBlank(tikFil)) {
            Workbook tikarWorkbook = WorkbookFactory.create(new FileInputStream(tikFil));
            printSheet("Hanar", "Försök 1", hanarWorkbook.getSheet("Försök 1"), out, omgangar);

            printSheet("Tikar", "Försök 1", tikarWorkbook.getSheet("Försök 1"), out, omgangar);

            printSheet("Hanar", "Försök 2", hanarWorkbook.getSheet("Försök 2"), out, omgangar);

            printSheet("Tikar", "Försök 2", tikarWorkbook.getSheet("Försök 2"), out, omgangar);

            printSemiSheet("Hanar", hanarWorkbook.getSheet("Semifinal"), out, omgangar);

            printSemiSheet("Tikar", tikarWorkbook.getSheet("Semifinal"), out, omgangar);

            printSheet("Hanar", "Final", hanarWorkbook.getSheet("Final"), out, omgangar);

            printSheet("Tikar", "Final", tikarWorkbook.getSheet("Final"), out, omgangar);
        } else {
            out.println("<h2>Försök 1</h2>");
            printSheet(null, "Försök 1", hanarWorkbook.getSheet("Försök 1"), out, omgangar);

            out.println("<h2>Försök 2</h2>");
            printSheet(null, "Försök 2", hanarWorkbook.getSheet("Försök 2"), out, omgangar);

            printSemiSheet(null, hanarWorkbook.getSheet("Semifinal"), out, omgangar);

            out.println("<h2>Final</h2>");
            printSheet(null, "Final", hanarWorkbook.getSheet("Final"), out, omgangar);
        }

        out.println("</body>");
        out.println("</html>");
        out.close();

        Race race = new Race(sektion, datum, omgangar);
        new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
//                .writerWithDefaultPrettyPrinter()
                .writeValue(new File(RESULT_JSON), race);

        upload();
        System.out.println("done");
    }

    private void upload() throws IOException {
        new FileUploader().upload(new File(RESULT_HTML), sektion, datum, FileUploader.UploadType.HTML);
        new FileUploader().upload(new File(RESULT_JSON), sektion, datum, FileUploader.UploadType.JSON);
    }

    private void printSheet(String kon, String namn, Sheet sheet, PrintStream out, List<Omgang> omgangar) {
        out.printf("<h2>%s%s%s</h2>%n", defaultString(kon), StringUtils.isNotBlank(kon) ? " " : "", namn);
        boolean inTable = false;
        List<Heat> heats = new ArrayList<>();
        Integer heatNr = null;
        String klass = null;
        String sponsor = null;
        List<Start> starter = null;
        for (Row row : sheet) {
            if (row.getZeroHeight()) {
                continue;
            }
            Cell firstCell = row.getCell(0);
            String firstCellValue = getCellValue(firstCell);
            if (firstCellValue.startsWith("HEAT") || firstCellValue.endsWith("-KLASS")) {
                out.printf("<b>%s</b><br>%n", firstCellValue);
                if (starter != null) {
                    heats.add(new Heat(heatNr, klass, sponsor, starter));
                }
                heatNr = null;
                klass = null;
                starter = new ArrayList<>();
                if (firstCellValue.startsWith("HEAT")) {
                    try {
                        heatNr = Integer.parseInt(firstCellValue.substring(4).trim());
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
                if (firstCellValue.endsWith("-KLASS")) {
                    klass = firstCellValue.substring(0, firstCellValue.length() - 6).trim();
                    sponsor = trimToNull(getCellValue(row.getCell(2)));
                }
                continue;
            } else if (firstCellValue.equals("R")) {
                out.println("<table border=1>");
                inTable = true;
            }
            if (inTable) {
                String farg = firstCellValue;
                Integer licensNr = null;
                String hundNamn;
                String hundKon = null;
                Double tid = null;
                Integer placering = null;
                String agare;
                String sektion;
                String kommentar;
                Boolean struken = null;
                Boolean licensHund = null;

                int basIndex = 1;
                Cell licensCell = getCell(sheet, row, basIndex);
                if (licensCell != null && licensCell.getCellType() == CellType.NUMERIC) {
                    licensNr = (int) licensCell.getNumericCellValue();
                    basIndex++;
                }
                hundNamn = trimToNull(getCellValue(row.getCell(basIndex)));
                basIndex++;
                String kanskeKon = getCellValue(row.getCell(basIndex));
                if (kanskeKon.equals("H") || kanskeKon.equals("T")) {
                    hundKon = kanskeKon;
                    basIndex++;
                }
                for (int cellIndex = basIndex; cellIndex <= basIndex + 1; cellIndex++) {
                    Cell tidPlacCell = getCell(sheet, row, cellIndex);
                    if (tidPlacCell != null && tidPlacCell.getCellType() == CellType.NUMERIC) {
                        double tidPlac = tidPlacCell.getNumericCellValue();
                        if (tidPlac >= 1 && tidPlac <= 4) {
                            placering = (int) tidPlac;
                        } else if (tidPlac > 4) {
                            tid = tidPlacCell.getNumericCellValue();
                        }
                    }
                }

                if (getCellValue(row.getCell(basIndex + 2)).length() < 3) {
                    basIndex++;
                }
                agare = trimToNull(getCellValue(row.getCell(basIndex + 2)));
                sektion = trimToNull(getCellValue(row.getCell(basIndex + 3)));
                kommentar = trimToNull(getCellValue(row.getCell(basIndex + 4)));

                out.println("<tr>");
                int lastCellNumWithContent = getLastCellNumWithContent(row);
                for (int cellIndex = 0; cellIndex <= lastCellNumWithContent; cellIndex++) {
                    printCell(out, sheet, row, cellIndex);
                    if (!sheet.isColumnHidden(cellIndex)) {
                        Cell cell = row.getCell(cellIndex);
                        if (isStrikethrough(cell)) {
                            struken = true;
                        } else if (isLicensHund(cell)) {
                            licensHund = true;
                        }
                    }
                }
                out.println("</tr>");

                if (firstCellValue.equals("S")) {
                    out.println("</table><br>");
                    inTable = false;
                }

                if (hundNamn != null) {
                    if (Boolean.TRUE.equals(licensHund)) {
                        licensHundNamn.add(hundNamn);
                    } else if (licensHundNamn.contains(hundNamn)) {
                        licensHund = true;
                    }
                    if (hundKon != null) {
                        hundNamnsKon.put(hundNamn, hundKon);
                    } else if (hundNamnsKon.containsKey(hundNamn)) {
                        hundKon = hundNamnsKon.get(hundNamn);
                    }
                }
                starter.add(new Start(farg.toUpperCase(), licensNr, hundNamn, hundKon, tid, placering, agare, sektion, kommentar, struken, licensHund));
            }
        }
        if (starter != null) {
            heats.add(new Heat(heatNr, klass, sponsor, starter));
        }
        omgangar.add(new Omgang(kon, namn, heats));
    }

    private void printSemiSheet(String kon, Sheet sheet, PrintStream out, List<Omgang> omgangar) {
        if (sheet == null || semiEmpty(sheet)) {
            return;
        }

        out.printf("<h2>%s%sSemifinal</h2>%n", defaultString(kon), StringUtils.isNotBlank(kon) ? " " : "");

        out.println("<table border=1>");
        int start1 = -1;
        int start2 = -1;
        for (Row row : sheet) {
            if (row.getZeroHeight()) {
                continue;
            }
            for (int cellIndex = start1 + 1; cellIndex < row.getLastCellNum(); cellIndex++) {
                if (start1 == -1 && getCellValue(row.getCell(cellIndex)).equals("R")) {
                    start1 = cellIndex;
                } else if (start2 == -1 && getCellValue(row.getCell(cellIndex)).equals("R")) {
                    start2 = cellIndex;
                }
            }
            if (start2 != -1) {
                break;
            }
        }
        List<Heat> heats = new ArrayList<>();
        for (int rowNr = 0; rowNr <= sheet.getLastRowNum(); rowNr++) {
            Row row = sheet.getRow(rowNr);
            if (row == null || row.getZeroHeight()) {
                continue;
            }
            if (row.getLastCellNum() >= 2) {
                if (StringUtils.isNotBlank(getCellValue(row.getCell(2))) || StringUtils.isNotBlank(getCellValue(row.getCell(0)))) {
                    out.println("<tr>");
                    int lastCellNumWithContent = getLastCellNumWithContent(row);
                    for (int cellIndex = 0; cellIndex <= lastCellNumWithContent; cellIndex++) {
                        printCell(out, sheet, row, cellIndex);
                    }
                    out.println("</tr>");
                }
            }
            for (Cell cell : row) {
                String cellValue = getCellValue(cell);
                if (cellValue.endsWith("KLASS") && cellValue.length() > 7) {
                    String klass = cellValue.substring(0, cellValue.length() - 5).trim();
                    for (int heatNr = 1; heatNr <= 2; heatNr++) {
                        int startCellIndex = heatNr == 1 ? start1 : start2;
                        int nextStartCellIndex = heatNr == 1 ? start2 : Integer.MAX_VALUE;
                        List<Start> starter = new ArrayList<>();
                        for (int heatRowNr = rowNr + 1; heatRowNr <= rowNr + 4; heatRowNr++) {
                            Row heatRow = sheet.getRow(heatRowNr);
                            if (heatRow == null || heatRow.getZeroHeight()) {
                                continue;
                            }
                            String farg = getCellValue(heatRow.getCell(startCellIndex));
                            Integer licensNr = null;
                            String hundNamn;
                            String hundKon = null;
                            Double tid = null;
                            Integer placering = null;
                            String kommentar = null;
                            Boolean struken = null;
                            Boolean licensHund = null;
                            for (int cellIndex = 0; cellIndex <= heatRow.getLastCellNum() && cellIndex < nextStartCellIndex; cellIndex++) {
                                if (!sheet.isColumnHidden(cellIndex)) {
                                    Cell heatCell = heatRow.getCell(cellIndex);
                                    if (isStrikethrough(heatCell)) {
                                        struken = true;
                                    } else if (isLicensHund(heatCell)) {
                                        licensHund = true;
                                    }
                                }
                            }

                            Cell licensCell = getCell(sheet, heatRow, startCellIndex + 1);
                            if (licensCell != null && licensCell.getCellType() == CellType.NUMERIC) {
                                licensNr = (int) licensCell.getNumericCellValue();
                            }
                            hundNamn = trimToNull(getCellValue(heatRow.getCell(startCellIndex + 2)));
                            for (int cellIndex = startCellIndex + 3; cellIndex <= startCellIndex + 4; cellIndex++) {
                                Cell tidPlacCell = getCell(sheet, heatRow, cellIndex);
                                if (tidPlacCell != null && tidPlacCell.getCellType() == CellType.NUMERIC) {
                                    double tidPlac = tidPlacCell.getNumericCellValue();
                                    if (tidPlac >= 1 && tidPlac <= 4) {
                                        placering = (int) tidPlac;
                                    } else if (tidPlac > 4) {
                                        tid = tidPlacCell.getNumericCellValue();
                                    }
                                }
                            }
                            if (startCellIndex + 5 < nextStartCellIndex) {
                                kommentar = trimToNull(getCellValue(heatRow.getCell(startCellIndex + 5)));
                            }
                            if (startCellIndex + 6 < nextStartCellIndex && kommentar == null) {
                                kommentar = trimToNull(getCellValue(heatRow.getCell(startCellIndex + 6)));
                            }

                            if (hundNamn != null) {
                                if (Boolean.TRUE.equals(licensHund)) {
                                    licensHundNamn.add(hundNamn);
                                } else if (licensHundNamn.contains(hundNamn)) {
                                    licensHund = true;
                                }
                                if (hundNamnsKon.containsKey(hundNamn)) {
                                    hundKon = hundNamnsKon.get(hundNamn);
                                }
                            }
                            starter.add(new Start(farg.toUpperCase(), licensNr, hundNamn, hundKon, tid, placering, null, null, kommentar, struken, licensHund));
                        }
                        heats.add(new Heat(heatNr, klass, null, starter));
                    }
                }
            }
        }
        out.println("</table><br>");
        omgangar.add(new Omgang(kon, "Semifinal", heats));
    }

    private static Cell getCell(Sheet sheet, Row row, int columnIndex) {
        if (sheet.isColumnHidden(columnIndex)) {
            return null;
        }
        return row.getCell(columnIndex);
    }

    private static void printCell(PrintStream out, Sheet sheet, Row row, int cellIndex) {
        if (sheet.isColumnHidden(cellIndex)) {
            return;
        }
        Cell cell = row.getCell(cellIndex);
        String css = "";
        if (isStrikethrough(cell)) {
            css = "style=\"text-decoration: line-through;\"";
        } else if (isLicensHund(cell)) {
            css = "style=\"text-decoration: underline; font-style:italic\"";
        } else if (isBold(cell)) {
            css = "style=\"font-weight:bold\"";
        }
        out.printf("<td %s>%s</td>", css, getCellValue(cell));
    }

    private static int getLastCellNumWithContent(Row row) {
        int lastCellNumWithContent = -1;
        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
            Cell cell = row.getCell(cellIndex);
            if (StringUtils.isNotBlank(getCellValue(cell))) {
                lastCellNumWithContent = cellIndex;
            }
        }
        return lastCellNumWithContent;
    }

    private boolean semiEmpty(Sheet sheet) {
        int columnToCheck = sheet.isColumnHidden(1) ? 2 : 1;
        for (Row row : sheet) {
            if (row.getZeroHeight()) {
                continue;
            }
            if (row.getLastCellNum() >= columnToCheck) {
                String cellValue = getCellValue(row.getCell(columnToCheck));
                if (StringUtils.isNotBlank(cellValue) && !cellValue.startsWith("HEAT")) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isStrikethrough(Cell cell) {
        if (cell == null) {
            return false;
        }
        CellStyle cellStyle = cell.getCellStyle();
        Font font = cell.getSheet().getWorkbook().getFontAt(cellStyle.getFontIndex());
        return font.getStrikeout();
    }

    private static boolean isLicensHund(Cell cell) {
        if (cell == null) {
            return false;
        }
        CellStyle cellStyle = cell.getCellStyle();
        Font font = cell.getSheet().getWorkbook().getFontAt(cellStyle.getFontIndex());
        return font.getUnderline() != Font.U_NONE || font.getItalic();
    }

    private static boolean isBold(Cell cell) {
        if (cell == null) {
            return false;
        }
        CellStyle cellStyle = cell.getCellStyle();
        Font font = cell.getSheet().getWorkbook().getFontAt(cellStyle.getFontIndex());
        return font.getBold();
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        } else if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.STRING) {
            return cell.getRichStringCellValue().toString();
        } else if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.ERROR) {
            return "";
        } else if (cell.getCellType() == CellType.NUMERIC) {
            double cellValue = cell.getNumericCellValue();
            if (cellValue > 4) {
                return String.format("%.2f", cellValue);
            } else {
                DataFormatter dataFormatter = new DataFormatter();
                return dataFormatter.formatCellValue(cell);
            }
        } else {
            DataFormatter dataFormatter = new DataFormatter();
            return dataFormatter.formatCellValue(cell);
        }
    }

    public void stop() {
        canceled = true;
    }
}

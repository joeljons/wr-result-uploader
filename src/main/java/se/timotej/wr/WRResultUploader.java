package se.timotej.wr;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public class WRResultUploader {
    private static final String RESULT_HTML = "result.html";
    private final String hanFil;
    private final String tikFil;
    private final String sektion;
    private final String datum;
    private FileTime hanModifiedTime;
    private FileTime tikModifiedTime;
    private volatile boolean canceled = false;

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: WRResultUploader <hanfil> <tikfil> <monitor true/false>");
            System.exit(1);
        }
        String hanFil = args[0];
        String tikFil = args[1];
        boolean monitor = Boolean.parseBoolean(args[2]);
        WRResultUploader wrResultUploader = new WRResultUploader(hanFil, tikFil, "Test", "2099-12-31");
        wrResultUploader.run();
        if (monitor) {
            wrResultUploader.monitor(null);
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

    public WRResultUploader(String hanFil, String tikFil, String sektion, String datum) {
        this.hanFil = hanFil;
        this.tikFil = tikFil;
        this.sektion = sektion;
        this.datum = datum;
    }

    public void run() throws IOException {
        System.out.println("WRResultUploader.run");
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
        out.println("</head>");
        out.println("<body>");
        out.printf("<h1>%s %s</h1>%n", sektion, datum);
        out.println("<p class='excludePrint'>Preliminära resultat<br>");
        out.println("<button onClick=\"reload()\">Ladda om</button></p>");
        Workbook hanarWorkbook = WorkbookFactory.create(new FileInputStream(hanFil));
        if (StringUtils.isNotBlank(tikFil)) {
            Workbook tikarWorkbook = WorkbookFactory.create(new FileInputStream(tikFil));
            out.println("<h2>Hanar Försök 1</h2>");
            printSheet(hanarWorkbook.getSheet("Försök 1"), out);

            out.println("<h2>Tikar Försök 1</h2>");
            printSheet(tikarWorkbook.getSheet("Försök 1"), out);

            out.println("<h2>Hanar Försök 2</h2>");
            printSheet(hanarWorkbook.getSheet("Försök 2"), out);

            out.println("<h2>Tikar Försök 2</h2>");
            printSheet(tikarWorkbook.getSheet("Försök 2"), out);

            out.println("<h2>Hanar Final</h2>");
            printSheet(hanarWorkbook.getSheet("Final"), out);

            out.println("<h2>Tikar Final</h2>");
            printSheet(tikarWorkbook.getSheet("Final"), out);
        } else {
            out.println("<h2>Försök 1</h2>");
            printSheet(hanarWorkbook.getSheet("Försök 1"), out);

            out.println("<h2>Försök 2</h2>");
            printSheet(hanarWorkbook.getSheet("Försök 2"), out);

            out.println("<h2>Final</h2>");
            printSheet(hanarWorkbook.getSheet("Final"), out);
        }

        out.println("</body>");
        out.println("</html>");
        out.close();
        upload();
        System.out.println("done");
    }

    private void upload() throws IOException {
        new FileUploader().upload(new File(RESULT_HTML), sektion, datum);
    }

    private static void printSheet(Sheet sheet, PrintStream out) {
        boolean inTable = false;
        for (Row row : sheet) {
            if (row.getZeroHeight()) {
                continue;
            }
            Cell firstCell = row.getCell(0);
            String firstCellValue = getCellValue(firstCell);
            if (firstCellValue.startsWith("HEAT") || firstCellValue.endsWith("-KLASS")) {
                out.printf("<b>%s</b><br>%n", firstCellValue);
                continue;
            } else if (firstCellValue.equals("R")) {
                out.println("<table border=1>");
                inTable = true;
            }
            if(inTable) {
                out.println("<tr>");
                int lastCellNumWithContent = -1;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (StringUtils.isNotBlank(getCellValue(cell))) {
                        lastCellNumWithContent = c;
                    }
                }
                for (int c = 0; c <= lastCellNumWithContent; c++) {
                    Cell cell = row.getCell(c);
                    String css = "";
                    if (isStrikethrough(cell)) {
                        css = "style=\"text-decoration: line-through;\"";
                    } else if (isUnderline(cell)) {
                        css = "style=\"text-decoration: underline; font-style:italic\"";
                    }
                    out.printf("<td %s>%s</td>", css, getCellValue(cell));
                }
                out.println("</tr>");

                if (firstCellValue.equals("S")) {
                    out.println("</table><br>");
                    inTable = false;
                }
            }
        }
    }

    private static boolean isStrikethrough(Cell cell) {
        if (cell == null) {
            return false;
        }
        CellStyle cellStyle = cell.getCellStyle();
        Font font = cell.getSheet().getWorkbook().getFontAt(cellStyle.getFontIndex());
        return font.getStrikeout();
    }

    private static boolean isUnderline(Cell cell) {
        if (cell == null) {
            return false;
        }
        CellStyle cellStyle = cell.getCellStyle();
        Font font = cell.getSheet().getWorkbook().getFontAt(cellStyle.getFontIndex());
        return font.getUnderline() != Font.U_NONE;
    }


    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        } else if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.STRING) {
            return cell.getRichStringCellValue().toString();
        } else if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.ERROR) {
            return "";
        } else {
            DataFormatter dataFormatter = new DataFormatter();
            return dataFormatter.formatCellValue(cell);
        }
    }

    public void stop() {
        canceled = true;
    }
}

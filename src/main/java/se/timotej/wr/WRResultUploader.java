package se.timotej.wr;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public class WRResultUploader {
    private final String hanFil;
    private final String tikFil;
    FileTime hanModifiedTime;
    FileTime tikModifiedTime;

    public static void main(String[] args) throws IOException, JSchException, SftpException {
        if(args.length<3) {
            System.out.println("Usage: -DftpPassword=<password> WRResultUploader <hanfil> <tikfil> <monitor true/false>");
            System.exit(1);
        }
        String hanFil = args[0];
        String tikFil = args[1];
        boolean monitor = Boolean.parseBoolean(args[2]);
        WRResultUploader wrResultUploader = new WRResultUploader(hanFil, tikFil);
        wrResultUploader.run();
        if (monitor) {
            wrResultUploader.monitor();
        }
        System.exit(0);
    }

    private void monitor() throws JSchException, SftpException, IOException {
        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (!hanModifiedTime.equals(Files.getLastModifiedTime(Path.of(hanFil)))
                    || !tikModifiedTime.equals(Files.getLastModifiedTime(Path.of(tikFil)))) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                run();
            }
        }
    }

    public WRResultUploader(String hanFil, String tikFil) {
        this.hanFil = hanFil;
        this.tikFil = tikFil;
    }

    private void run() throws IOException, JSchException, SftpException {
        System.out.println("WRResultUploader.run");
        hanModifiedTime = Files.getLastModifiedTime(Path.of(hanFil));
        tikModifiedTime = Files.getLastModifiedTime(Path.of(tikFil));
//        System.out.println("hanModifiedTime = " + hanModifiedTime);
//        System.out.println("tikModifiedTime = " + tikModifiedTime);

        PrintStream out = new PrintStream("index.html");
        out.println("<html>");
        out.println("<head>");
        out.println("<meta charset=\"utf-8\" /> ");
        out.println("<style>");
        out.println("@media print {");
        out.println("    .excludePrint { display: none; }");
        out.println("    h2 { page-break-before: always; break-before: page; }");
        out.println("    h2:first-of-type { page-break-before: avoid; break-before: avoid; }");
        out.println("}");
        out.println("</style>");

        out.println("</head>");
        out.println("<body>");
        out.println("<h1>Södertälje 2025-05-24</h1>");
        out.println("<p class='excludePrint'>Preliminära resultat<br>");
        out.println("<button onClick='location.href=\"?a=\"+Math.random()'>Ladda om</button></p>");
        Workbook hanarWorkbook = WorkbookFactory.create(new FileInputStream(hanFil));
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
        out.println("</body>");
        out.println("</html>");
        out.close();
        upload();
        System.out.println("done");
    }

    private void upload() throws JSchException, SftpException {
        new FileUploader().upload(new File("index.html"));
    }

    private static void printSheet(Sheet sheet, PrintStream out) {
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
            }
            out.println("<tr>");
            for (Cell cell : row) {
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
        } else {
            DataFormatter dataFormatter = new DataFormatter();
            return dataFormatter.formatCellValue(cell);
        }
    }

}

package API_BoPhieu.service.file;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import API_BoPhieu.exception.FileException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileImportServiceImpl implements FileImportService {

    private static final String EMAIL_COLUMN = "Email";

    @Override
    public List<String> extractEmails(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new FileException("Tên file không hợp lệ");
        }

        String extension = "";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            extension = filename.substring(i + 1).toLowerCase();
        }

        if ("csv".equals(extension)) {
            return readCsv(file);
        } else if ("xlsx".equals(extension) || "xls".equals(extension)) {
            return readExcel(file);
        } else {
            throw new FileException(
                    "Định dạng file không được hỗ trợ. Vui lòng sử dụng file Excel hoặc CSV");
        }
    }

    private List<String> readCsv(MultipartFile file) {
        List<String> emails = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                CSVParser csvParser = new CSVParser(fileReader,
                        CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                                .setIgnoreHeaderCase(true).setTrim(true).build())) {

            // Check if Email header exists
            if (!csvParser.getHeaderMap().keySet().stream()
                    .anyMatch(h -> h.equalsIgnoreCase(EMAIL_COLUMN))) {
                throw new FileException("File không chứa cột 'Email'");
            }

            for (CSVRecord csvRecord : csvParser) {
                // Safe check for mapped column
                try {
                    String email = csvRecord.get(EMAIL_COLUMN);
                    if (email != null && !email.trim().isEmpty()) {
                        emails.add(email.trim());
                    }
                } catch (IllegalArgumentException e) {
                    // Try case insensitive manually if library fails or just continue
                    // library handles ignoreHeaderCase, but let's be safe
                    String email = null;
                    for (String header : csvParser.getHeaderMap().keySet()) {
                        if (header.equalsIgnoreCase(EMAIL_COLUMN)) {
                            email = csvRecord.get(header);
                            break;
                        }
                    }
                    if (email != null && !email.trim().isEmpty()) {
                        emails.add(email.trim());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Lỗi khi đọc file CSV: ", e);
            throw new FileException("Không thể đọc file CSV: " + e.getMessage());
        }
        return emails;
    }

    private List<String> readExcel(MultipartFile file) {
        List<String> emails = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            int emailColumnIndex = -1;

            if (rows.hasNext()) {
                Row headerRow = rows.next();
                for (Cell cell : headerRow) {
                    String cellValue = getCellValue(cell);
                    if (EMAIL_COLUMN.equalsIgnoreCase(cellValue)) {
                        emailColumnIndex = cell.getColumnIndex();
                        break;
                    }
                }
            }

            if (emailColumnIndex == -1) {
                throw new FileException("File không chứa cột 'Email'");
            }

            while (rows.hasNext()) {
                Row currentRow = rows.next();
                Cell cell = currentRow.getCell(emailColumnIndex,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null) {
                    String email = getCellValue(cell);
                    if (email != null && !email.trim().isEmpty()) {
                        emails.add(email.trim());
                    }
                }
            }

        } catch (IOException e) {
            log.error("Lỗi khi đọc file Excel: ", e);
            throw new FileException("Không thể đọc file Excel: " + e.getMessage());
        }
        return emails;
    }

    private String getCellValue(Cell cell) {
        if (cell == null)
            return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue()); // Assuming email is
                                                                          // string, but if numbers
            default:
                return "";
        }
    }
}


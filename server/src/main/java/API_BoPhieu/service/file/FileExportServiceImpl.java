package API_BoPhieu.service.file;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import API_BoPhieu.dto.attendant.ParticipantResponse;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileExportServiceImpl implements FileExportService {

    private static final String SHEET_NAME = "Danh sách người tham dự";
    private static final String COLUMN_NAME = "Tên";
    private static final String COLUMN_EMAIL = "Email";
    private static final String COLUMN_STATUS = "Trạng thái";
    private static final String COLUMN_CHECK_IN_TIME = "Thời gian check-in";
    private static final String STATUS_CHECKED_IN = "Đã check-in";
    private static final String STATUS_NOT_CHECKED_IN = "Chưa check-in";
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("vi-VN"))
                    .withZone(ZoneId.of("Asia/Ho_Chi_Minh")); // Ép đúng múi giờ VN

    @Override
    public byte[] exportParticipantsToExcel(final List<ParticipantResponse> participants,
            final String eventTitle) throws IOException {
        log.debug("Bắt đầu xuất {} người tham dự ra file Excel cho sự kiện: {}",
                participants.size(), eventTitle);

        try (final Workbook workbook = new XSSFWorkbook();
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            final Sheet sheet = workbook.createSheet(SHEET_NAME);

            // Create header style
            final CellStyle headerStyle = createHeaderStyle(workbook);
            final CellStyle dataStyle = createDataStyle(workbook);

            // Create header row
            createHeaderRow(sheet, headerStyle);

            // Create data rows
            int rowNum = 1;
            for (final ParticipantResponse participant : participants) {
                createDataRow(sheet, participant, dataStyle, rowNum++);
            }

            // Auto-size columns
            autoSizeColumns(sheet);

            // Write to byte array
            workbook.write(outputStream);
            final byte[] excelBytes = outputStream.toByteArray();

            log.info("Đã xuất thành công {} người tham dự ra file Excel ({} bytes)",
                    participants.size(), excelBytes.length);
            return excelBytes;
        } catch (final IOException e) {
            log.error("Lỗi khi xuất file Excel: ", e);
            throw new IOException("Không thể tạo file Excel: " + e.getMessage(), e);
        }
    }

    /**
     * Creates header style with bold font and center alignment. Follows DRY principle by
     * centralizing style creation.
     */
    private CellStyle createHeaderStyle(final Workbook workbook) {
        final CellStyle style = workbook.createCellStyle();
        final Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /**
     * Creates data style for regular cells.
     */
    private CellStyle createDataStyle(final Workbook workbook) {
        final CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    /**
     * Creates the header row with column names.
     */
    private void createHeaderRow(final Sheet sheet, final CellStyle headerStyle) {
        final Row headerRow = sheet.createRow(0);

        int colNum = 0;
        createCell(headerRow, colNum++, COLUMN_NAME, headerStyle);
        createCell(headerRow, colNum++, COLUMN_EMAIL, headerStyle);
        createCell(headerRow, colNum++, COLUMN_STATUS, headerStyle);
        createCell(headerRow, colNum, COLUMN_CHECK_IN_TIME, headerStyle);
    }

    /**
     * Creates a data row for a participant. Follows KISS principle with straightforward mapping.
     */
    private void createDataRow(final Sheet sheet, final ParticipantResponse participant,
            final CellStyle dataStyle, final int rowNum) {
        final Row row = sheet.createRow(rowNum);

        int colNum = 0;

        // Tên
        final String name = participant.getUser() != null ? participant.getUser().getName() : "N/A";
        createCell(row, colNum++, name, dataStyle);

        // Email
        final String email =
                participant.getUser() != null ? participant.getUser().getEmail() : "N/A";
        createCell(row, colNum++, email, dataStyle);

        // Trạng thái
        final String status =
                participant.getCheckInTime() != null ? STATUS_CHECKED_IN : STATUS_NOT_CHECKED_IN;
        createCell(row, colNum++, status, dataStyle);

        // Thời gian check-in
        final String checkInTime = formatCheckInTime(participant.getCheckInTime());
        createCell(row, colNum, checkInTime, dataStyle);
    }

    /**
     * Creates a cell with value and style. Follows DRY principle by centralizing cell creation.
     */
    private void createCell(final Row row, final int columnIndex, final String value,
            final CellStyle style) {
        final Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    /**
     * Formats check-in time to Vietnamese date-time format. Returns empty string if check-in time
     * is null.
     */
    private String formatCheckInTime(final Instant checkInTime) {
        if (checkInTime == null) {
            return "";
        }
        return DATE_TIME_FORMATTER.format(checkInTime);
    }

    /**
     * Auto-sizes all columns in the sheet for better readability.
     */
    private void autoSizeColumns(final Sheet sheet) {
        final int numberOfColumns = 4; // Name, Email, Status, Check-in Time
        for (int i = 0; i < numberOfColumns; i++) {
            sheet.autoSizeColumn(i);
            // Add some padding
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
        }
    }
}


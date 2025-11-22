package API_BoPhieu.service.attendant;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

public class ByteArrayMultipartFile implements MultipartFile {

    private final byte[] content;
    private final String name;
    private final String originalFilename;
    private final String contentType;

    public ByteArrayMultipartFile(final byte[] content, final String originalFilename) {
        this.content = content;
        this.name = originalFilename;
        this.originalFilename = originalFilename;
        this.contentType = determineContentType(originalFilename);
    }

    private String determineContentType(final String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        final String lowerName = filename.toLowerCase();
        if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lowerName.endsWith(".csv")) {
            return "text/csv";
        }
        return "application/octet-stream";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return content == null || content.length == 0;
    }

    @Override
    public long getSize() {
        return content != null ? content.length : 0;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return content != null ? content.clone() : new byte[0];
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(content != null ? content : new byte[0]);
    }

    @Override
    public void transferTo(final File dest) throws IOException, IllegalStateException {
        throw new UnsupportedOperationException("transferTo is not supported for ByteArrayMultipartFile");
    }
}


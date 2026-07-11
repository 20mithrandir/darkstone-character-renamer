import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AnalyzeSave {
    private static final int XOR_KEY = 0xEB;
    private static final int MAX_NAME_LEN = 50;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Usage: java AnalyzeSave <file>");
            return;
        }

        byte[] fileData = Files.readAllBytes(Path.of(args[0]));
        System.out.println("Analyzing: " + args[0] + " (Size: " + fileData.length + ")");

        for (int i = 2; i < fileData.length - MAX_NAME_LEN; i++) {
            analyzePotentialName(fileData, i);
        }
    }

    private static void analyzePotentialName(byte[] fileData, int offset) {
        int marker1 = fileData[offset - 1] & 0xFF;
        int marker2 = fileData[offset - 2] & 0xFF;
        int marker3 = (offset >= 3) ? (fileData[offset - 3] & 0xFF) : 0;
        
        int firstChar = (fileData[offset] & 0xFF) ^ XOR_KEY;

        int len = 0;
        while (len < MAX_NAME_LEN && offset + len < fileData.length) {
            int val = (fileData[offset + len] & 0xFF) ^ XOR_KEY;
            if (val == 0) break;
            if (val < 32 && val != 0) break; 
            len++;
        }

        int paddingCount = 0;
        int pos = offset + len;
        while (pos < fileData.length && paddingCount < 30) {
            if ((fileData[pos] & 0xFF) == XOR_KEY) {
                paddingCount++;
                pos++;
            } else break;
        }

        if (len > 0 && firstChar >= 32 && paddingCount >= 3) {
            byte[] decoded = new byte[len];
            for (int i = 0; i < len; i++) {
                decoded[i] = (byte) ((fileData[offset + i] & 0xFF) ^ XOR_KEY);
            }
            String name = new String(decoded, java.nio.charset.StandardCharsets.ISO_8859_1);
             System.out.printf("Offset: 0x%08X | Markers: %02X %02X %02X | Name: %-20s | Len: %2d | Padding: %2d | Prev: %02X %02X %02X %02X%n",
                offset, marker3, marker2, marker1, name, len, paddingCount,
                (offset >= 4 ? fileData[offset-4] & 0xFF : 0),
                (offset >= 3 ? fileData[offset-3] & 0xFF : 0),
                (offset >= 2 ? fileData[offset-2] & 0xFF : 0),
                (offset >= 1 ? fileData[offset-1] & 0xFF : 0));
        }
    }
}

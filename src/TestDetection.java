import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TestDetection {
    private static final int XOR_KEY = 0xEB;
    private static final int MAX_NAME_LEN = 50;

    public static void main(String[] args) throws IOException {
        testFile("examples/characters.lpl.carnivoredude");
        testFile("examples/characters.lpl.peter");
    }

    private static void testFile(String filePath) throws IOException {
        byte[] fileData = Files.readAllBytes(Path.of(filePath));
        List<String> foundNames = new ArrayList<>();
        
        for (int i = 2; i < fileData.length - MAX_NAME_LEN; i++) {
            int marker1 = fileData[i - 1] & 0xFF;
            if (marker1 == 0xAF || marker1 == 0xE0 || marker1 == 0xF6 || marker1 == 0xF7 || marker1 == 0xF1) {
                int marker2 = fileData[i - 2] & 0xFF;
                // Filter based on refined marker patterns discovered in example files
                if (marker2 == 0xB4 || marker2 == 0xB3 || marker2 == 0xB7 || marker2 == 0xB5 || marker2 == 0xB6 ||
                    marker2 == 0x1A || marker2 == 0x17 || marker2 == 0x13 || 
                    marker2 == 0x19 || marker2 == 0xA2 || marker2 == 0xB0 || 
                    marker2 == 0xE1 || marker2 == 0xAD) {
                    
                    String name = checkName(fileData, i);
                    if (name != null) {
                        foundNames.add(name);
                    }
                }
            }
        }
        
        System.out.println("File: " + filePath + " | Found: " + foundNames.size());
        for (String n : foundNames) {
            System.out.println(" - " + n);
        }
    }

    private static String checkName(byte[] fileData, int offset) {
        int firstChar = (fileData[offset] & 0xFF) ^ XOR_KEY;
        // Basic printable check (allow extended ASCII)
        if (firstChar < 32) return null;
        // Known garbage prefixes in metadata blocks
        if (firstChar == 'F' || firstChar == '7' || firstChar == '\\' || firstChar == 'D') return null;

        int len = 0;
        while (len < MAX_NAME_LEN && offset + len < fileData.length) {
            int val = (fileData[offset + len] & 0xFF) ^ XOR_KEY;
            if (val == 0 || val < 32) break;
            len++;
        }

        if (len == 0) return null;

        int paddingCount = 0;
        int pos = offset + len;
        while (pos < fileData.length && paddingCount < 25) {
            if ((fileData[pos] & 0xFF) == XOR_KEY) {
                paddingCount++;
                pos++;
            } else break;
        }

        // Strong requirement for XOR-null (0xEB) padding to confirm it's a character slot
        if (paddingCount >= 15) {
            byte[] decoded = new byte[len];
            for (int j = 0; j < len; j++) {
                decoded[j] = (byte) ((fileData[offset + j] & 0xFF) ^ XOR_KEY);
            }
            return new String(decoded, java.nio.charset.StandardCharsets.ISO_8859_1).trim();
        }
        return null;
    }
}

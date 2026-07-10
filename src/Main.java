import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main extends JFrame {
    private static final int XOR_KEY = 0xEB;
    private static final int MAX_NAME_LEN = 50;
    private static final int NAME_FIELD_OFFSET_FROM_MATCH = -2; // Not used anymore but kept for compatibility if needed
    
    private static final int PAGE_SIZE = 0x65E0;
    private static final int SLOT_SIZE = 0x8C;
    private static final int START_OFFSET = 0x669A;
    private static final int MAX_PAGES = 50;
    private static final int MAX_SLOTS = 186;

    private Path currentLplPath;
    private byte[] fileData;
    private final List<CharacterRecord> characterRecords = new ArrayList<>();
    
    private final JList<CharacterRecord> characterList;
    private final JTextField nameField;
    private final JButton saveButton;
    private final JLabel statusLabel;

    public Main() {
        setTitle("DarkStone Character Renamer");
        setSize(800, 450); // Increased default size
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        characterList = new JList<>();
        nameField = new JTextField();
        saveButton = new JButton("Save Name");
        statusLabel = new JLabel("Searching for DarkStone...");

        characterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        characterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CharacterRecord selected = characterList.getSelectedValue();
                if (selected != null) {
                    nameField.setText(selected.name);
                    saveButton.setEnabled(true);
                } else {
                    nameField.setText("");
                    saveButton.setEnabled(false);
                }
            }
        });

        saveButton.setEnabled(false);
        saveButton.addActionListener(e -> performRename());

        JButton quitButton = new JButton("Quit");
        quitButton.addActionListener(e -> System.exit(0));

        JPanel rightPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 5, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        rightPanel.add(new JLabel("Change Name To:"), gbc);

        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 10, 10, 10);
        rightPanel.add(nameField, gbc);

        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(10, 10, 5, 10);
        rightPanel.add(saveButton, gbc);

        gbc.gridy = 3;
        gbc.weighty = 1.0; // Push everything up
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(5, 10, 10, 10);
        rightPanel.add(quitButton, gbc);

        JScrollPane scrollPane = new JScrollPane(characterList);
        scrollPane.setMinimumSize(new Dimension(300, 0));
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, rightPanel);
        splitPane.setDividerLocation(350);
        splitPane.setResizeWeight(0.3); // Favor the list panel but allow some right-panel growth

        add(splitPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        
        Timer timer = new Timer(500, e -> findAndLoadFile());
        timer.setRepeats(false);
        timer.start();
    }

    private void findAndLoadFile() {
        // 1. Check current directory first
        Path currentDirFile = Path.of("save", "characters", "characters.lpl");
        if (Files.exists(currentDirFile)) {
            loadLplFile(currentDirFile);
            return;
        }
        currentDirFile = Path.of("characters.lpl");
        if (Files.exists(currentDirFile)) {
            loadLplFile(currentDirFile);
            return;
        }

        // 2. Dynamic scan on all root drives
        File[] drives = File.listRoots();
        if (drives != null) {
            // Re-order to prioritize C:
            List<File> driveList = new ArrayList<>(List.of(drives));
            driveList.sort((a, b) -> {
                if (a.getPath().startsWith("C")) return -1;
                if (b.getPath().startsWith("C")) return 1;
                return a.getPath().compareTo(b.getPath());
            });

            for (File drive : driveList) {
                Path found = scanForFile(drive.toPath(), 3); // Depth 3 to find it in "Games/Darkstone" or "Darkstone"
                if (found != null) {
                    loadLplFile(found);
                    return;
                }
            }
        }

        // 3. Fallback to File Chooser
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Darkstone characters.lpl");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadLplFile(chooser.getSelectedFile().toPath());
        } else {
            statusLabel.setText("No file loaded. Please select characters.lpl manually.");
        }
    }

    private Path scanForFile(Path root, int maxDepth) {
        try {
            File rootFile = root.toFile();
            if (!rootFile.exists() || !rootFile.canRead()) return null;

            File saveDir = new File(rootFile, "save");
            if (saveDir.exists()) {
                File charDir = new File(saveDir, "characters");
                if (charDir.exists()) {
                    File lpl = new File(charDir, "characters.lpl");
                    if (lpl.exists()) return lpl.toPath();
                }
            }

            File[] topLevel = rootFile.listFiles();
            if (topLevel == null) return null;

            for (File f : topLevel) {
                if (f.isDirectory() && !f.isHidden() && f.canRead()) {
                    // Check for [DRIVE]\[DIR]\save\characters\characters.lpl
                    File sDir = new File(f, "save");
                    if (sDir.exists()) {
                        File cDir = new File(sDir, "characters");
                        if (cDir.exists()) {
                            File l = new File(cDir, "characters.lpl");
                            if (l.exists()) return l.toPath();
                        }
                    }
                    
                    // One level deeper: [DRIVE]\[DIR]\[DIR]\save\characters\characters.lpl
                    File[] sub = f.listFiles();
                    if (sub != null) {
                        for (File s : sub) {
                            if (s.isDirectory() && !s.isHidden() && s.canRead()) {
                                File ssDir = new File(s, "save");
                                if (ssDir.exists()) {
                                    File ccDir = new File(ssDir, "characters");
                                    if (ccDir.exists()) {
                                        File ll = new File(ccDir, "characters.lpl");
                                        if (ll.exists()) return ll.toPath();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private void loadLplFile(Path path) {
        try {
            currentLplPath = path;
            fileData = Files.readAllBytes(path);
            characterRecords.clear();

            // We use a linear scan but with extremely high quality requirements to avoid garbage.
            // A valid name MUST be preceded by 0xAF and followed by significant 0xEB padding.
            for (int i = 1; i < fileData.length - MAX_NAME_LEN; i++) {
                if ((fileData[i - 1] & 0xFF) == 0xAF) {
                    checkAndAddName(i);
                }
            }

            DefaultListModel<CharacterRecord> model = new DefaultListModel<>();
            for (CharacterRecord r : characterRecords) model.addElement(r);
            characterList.setModel(model);
            statusLabel.setText("Loaded: " + path);

        } catch (IOException e) {
            statusLabel.setText("Error loading file: " + e.getMessage());
        }
    }

    private void checkAndAddName(int offset) {
        if (offset <= 1 || offset >= fileData.length) return;

        // CRITICAL FILTER: Valid name slots in Darkstone are always preceded by a 0xAF byte (separator)
        // AND another byte (usually 0xB7, 0xB4, 0xB3) which decodes to structural markers.
        // Garbage names (like metadata blocks) have 0xAF but different sequences.
        int marker1 = fileData[offset - 1] & 0xFF;
        int marker2 = fileData[offset - 2] & 0xFF;
        if (marker1 != 0xAF) return;
        
        // Marker 2 check: legitimate slots have 0xB4, 0xB3, or 0xB7 before 0xAF.
        if (marker2 != 0xB4 && marker2 != 0xB3 && marker2 != 0xB7) return;

        // ANTI-GARBAGE: Names must start with a valid printable character (A-Z, a-z, space, etc.)
        int firstChar = (fileData[offset] & 0xFF) ^ XOR_KEY;
        if (firstChar < 32 || firstChar > 126) return;
        
        // Metadata filtering: Skip common non-name printable characters found in metadata headers.
        // We allow 'D' now (since DAMIAN starts with D), but rely on marker2 and padding to filter metadata.
        if (firstChar == 'F' || firstChar == '7' || firstChar == '\\') return;
        
        if (firstChar == 'P') {
            if (offset + 2 < fileData.length) {
                int secondChar = (fileData[offset + 1] & 0xFF) ^ XOR_KEY;
                int thirdChar = (fileData[offset + 2] & 0xFF) ^ XOR_KEY;
                if (secondChar == '7' && thirdChar == '\\') return;
            }
        }

        // Check for duplicate or partial names
        for (CharacterRecord r : characterRecords) {
            if (Math.abs(r.offset - offset) < 32) return;
        }

        int len = 0;
        while (len < MAX_NAME_LEN && offset + len < fileData.length) {
            int b = fileData[offset + len] & 0xFF;
            int val = b ^ XOR_KEY;
            if (val == 0) break; // XOR null
            if (val < 32 || val > 126) break; // Non-printable
            len++;
        }

        // HEURISTIC: Genuine character names are followed by significant 0xEB padding.
        if (len > 0) {
            int paddingCount = 0;
            int pos = offset + len;
            while (pos < fileData.length && paddingCount < 25) {
                if ((fileData[pos] & 0xFF) == XOR_KEY) {
                    paddingCount++;
                    pos++;
                } else {
                    break;
                }
            }

            // Valid character slots have significant 0xEB padding after the name.
            if (paddingCount >= 15 || (len > 30 && paddingCount >= 4)) {
                String name = decode(fileData, offset, len).trim();
                if (!name.isEmpty()) {
                    characterRecords.add(new CharacterRecord(name, offset));
                }
            }
        }
    }

    private void performRename() {
        CharacterRecord selected = characterList.getSelectedValue();
        String newName = nameField.getText();
        if (selected == null || newName.isEmpty()) return;

        try {
            // Backup with timestamp
            long timestamp = System.currentTimeMillis() / 1000L;
            Path backup = currentLplPath.resolveSibling(currentLplPath.getFileName().toString() + ".bak." + timestamp);
            Files.copy(currentLplPath, backup, StandardCopyOption.REPLACE_EXISTING);

            // Applying the change
            byte[] encoded = encode(newName);
            
            // Fixed buffer size for names in Darkstone is approximately 60 bytes.
            // We use a safe 60-byte buffer to avoid overwriting structural data.
            int totalBuffer = 60; 
            
            for (int i = 0; i < totalBuffer; i++) {
                if (i < encoded.length) {
                    fileData[selected.offset + i] = encoded[i];
                } else {
                    fileData[selected.offset + i] = (byte) XOR_KEY;
                }
            }

            Files.write(currentLplPath, fileData);
            selected.name = newName;
            characterList.repaint();
            statusLabel.setText("Name saved. Backup: " + backup.getFileName());
            
        } catch (IOException e) {
            statusLabel.setText("Error saving: " + e.getMessage());
        }
    }

    private String decode(byte[] data, int offset, int len) {
        byte[] decoded = new byte[len];
        for (int i = 0; i < len; i++) {
            decoded[i] = (byte) ((data[offset + i] & 0xFF) ^ XOR_KEY);
        }
        return new String(decoded);
    }

    private byte[] encode(String name) {
        byte[] bytes = name.getBytes();
        byte[] encoded = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            encoded[i] = (byte) ((bytes[i] & 0xFF) ^ XOR_KEY);
        }
        return encoded;
    }

    private boolean isPrintable(String s) {
        for (char c : s.toCharArray()) {
            if (c < 32 || c > 126) return false;
        }
        return true;
    }

    private static class CharacterRecord {
        String name;
        int offset;

        CharacterRecord(String name, int offset) {
            this.name = name;
            this.offset = offset;
        }

        @Override
        public String toString() {
            return String.format("%s (0x%08X)", name, offset);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
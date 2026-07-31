import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class Main extends JFrame {
    private static final int XOR_KEY = 0xEB;
    private static final int MAX_NAME_LEN = 50;
    private static final String PREF_LAST_FILE = "last_lpl_file";

    private Path currentLplPath;
    private byte[] fileData;
    private final List<CharacterRecord> characterRecords = new ArrayList<>();
    
    private final JList<CharacterRecord> characterList;
    private final JTextField nameField;
    private final JButton saveButton;
    private final JLabel statusLabel;
    private final JLabel countLabel;
    private final JTextField searchField;

    public Main() {
        setTitle("DarkStone Character Renamer v1.0.2");
        setSize(800, 450);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        characterList = new JList<>();
        nameField = new JTextField();
        saveButton = new JButton("Save Name");
        JButton openButton = new JButton("Open Save...");
        statusLabel = new JLabel("Searching for DarkStone...");
        countLabel = new JLabel("Characters: 0");
        searchField = new JTextField();

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

        openButton.addActionListener(e -> openManualFile());

        JButton searchButton = new JButton("Search Name");
        searchButton.addActionListener(e -> searchByName());

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
        gbc.insets = new Insets(5, 10, 5, 10);
        rightPanel.add(saveButton, gbc);

        // Search by name section
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(10, 10, 0, 10);
        rightPanel.add(new JLabel("Search Name:"), gbc);

        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 10, 5, 10);
        rightPanel.add(searchField, gbc);

        gbc.gridy = 5;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(0, 10, 10, 10);
        rightPanel.add(searchButton, gbc);

        gbc.gridy = 6;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.insets = new Insets(5, 10, 10, 10);
        rightPanel.add(quitButton, gbc);

        JScrollPane scrollPane = new JScrollPane(characterList);
        scrollPane.setMinimumSize(new Dimension(300, 0));
        
        // Panel that contains list + count label
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(scrollPane, BorderLayout.CENTER);
        leftPanel.add(countLabel, BorderLayout.SOUTH);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(350);
        splitPane.setResizeWeight(0.3);

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        footerPanel.add(statusLabel, BorderLayout.CENTER);
        footerPanel.add(openButton, BorderLayout.EAST);

        add(splitPane, BorderLayout.CENTER);
        add(footerPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
        
        Timer timer = new Timer(500, e -> findAndLoadFile());
        timer.setRepeats(false);
        timer.start();
    }

    private void findAndLoadFile() {
        Preferences prefs = Preferences.userNodeForPackage(Main.class);
        String lastFile = prefs.get(PREF_LAST_FILE, null);
        if (lastFile != null) {
            Path lastPath = Paths.get(lastFile);
            if (Files.exists(lastPath)) {
                loadLplFile(lastPath);
                return;
            }
        }

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

        File[] drives = File.listRoots();
        if (drives != null) {
            List<File> driveList = new ArrayList<>(List.of(drives));
            driveList.sort((a, b) -> {
                if (a.getPath().startsWith("C")) return -1;
                if (b.getPath().startsWith("C")) return 1;
                return a.getPath().compareTo(b.getPath());
            });

            for (File drive : driveList) {
                Path found = scanForFile(drive.toPath(), 3);
                if (found != null) {
                    loadLplFile(found);
                    return;
                }
            }
        }

        openManualFile();
    }

    private void openManualFile() {
        JFileChooser chooser = new JFileChooser();
        if (currentLplPath != null && Files.exists(currentLplPath)) {
            chooser.setCurrentDirectory(currentLplPath.getParent().toFile());
        }
        chooser.setDialogTitle("Select Darkstone characters.lpl");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadLplFile(chooser.getSelectedFile().toPath());
        } else if (currentLplPath == null) {
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
                    File sDir = new File(f, "save");
                    if (sDir.exists()) {
                        File cDir = new File(sDir, "characters");
                        if (cDir.exists()) {
                            File l = new File(cDir, "characters.lpl");
                            if (l.exists()) return l.toPath();
                        }
                    }
                    
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

    private static final int SLOT_SIZE = 0x65E0;

    private static boolean isValidNameStart(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || c == 0xC4 || c == 0xD6 || c == 0xDC  // Ä Ö Ü
                || c == 0xE4 || c == 0xF6 || c == 0xFC  // ä ö ü
                || c == 0xDF;                             // ß
    }

    private static boolean isValidNameChar(int c) {
        return isValidNameStart(c) || c == ' ' || c == '-' || c == '\'';
    }

    // Returns true when a decoded candidate is a structural artifact, not a real character name.
    //
    // "DD" suffix: raw bytes 0xAF 0xAF appear before an empty name buffer (the game's
    // default fill for unused slots).  The slot scan mistakes these pre-marker bytes for
    // the tail of a name.  Real character names never end in "DD".
    //
    // High-run ratio: long runs of one letter (e.g. "DDDDDDD" from 0xAF fill, "FFFFFFF"
    // from 0xAD fill) indicate the scan walked across structural padding bytes.
    private static boolean isStructuralFill(String name) {
        // Artifact: ends with the two-byte 0xAF 0xAF pre-marker pattern (decoded as "DD")
        if (name.endsWith("DD")) return true;

        // Artifact: dominated by a single repeated character (≥6 run, >50% of name)
        if (name.length() < 3) return false;
        int maxRun = 1, curRun = 1;
        for (int i = 1; i < name.length(); i++) {
            if (name.charAt(i) == name.charAt(i - 1)) {
                curRun++;
                if (curRun > maxRun) maxRun = curRun;
            } else {
                curRun = 1;
            }
        }
        return maxRun >= 6 && (maxRun * 100 / name.length()) > 50;
    }

    private void loadLplFile(Path path) {
        try {
            currentLplPath = path;
            fileData = Files.readAllBytes(path);
            characterRecords.clear();

            // FIRST PASS: marker-based scan (pre-byte patterns + padding)
            List<CharacterRecord> allCandidates = new ArrayList<>();

            for (int i = 2; i < fileData.length - MAX_NAME_LEN; i++) {
                checkAndAddName(i, true, allCandidates);
            }

            // Remove candidates where a shorter valid name ends at the same position,
            // but only when the shorter name has a primary marker (0xAF/0xE0/0xF6/0xF7/0xF1).
            // 0xAE is an extended marker that coincides with XOR-encoded letters inside real
            // names (e.g. 'E'^0xEB=0xAE), so a shorter suffix with 0xAE is a false positive,
            // not a reason to discard the longer real name.
            List<CharacterRecord> filteredCandidates = new ArrayList<>();
            for (CharacterRecord candidate : allCandidates) {
                int candidateEnd = candidate.offset + candidate.name.length();
                boolean hasShorterPrimaryMarkerSuffix = false;
                for (CharacterRecord other : allCandidates) {
                    if (other.offset > candidate.offset
                            && other.offset < candidateEnd
                            && other.offset + other.name.length() == candidateEnd
                            && other.marker1 != 0xAE) {
                        hasShorterPrimaryMarkerSuffix = true;
                        break;
                    }
                }
                if (!hasShorterPrimaryMarkerSuffix) {
                    filteredCandidates.add(candidate);
                }
            }

            // SECOND PASS: proximity dedup — keep one character per cluster
            characterRecords.clear();
            for (CharacterRecord candidate : filteredCandidates) {
                boolean hasConflict = false;
                for (CharacterRecord accepted : characterRecords) {
                    if (Math.abs(accepted.offset - candidate.offset) < 32) {
                        hasConflict = true;
                        break;
                    }
                }
                if (!hasConflict) {
                    characterRecords.add(candidate);
                }
            }

            // THIRD PASS: slot-based scan — navigate directly to each slot and
            // look for clean names (alphabetic + XOR padding) without requiring
            // specific pre-byte marker patterns.  Catches characters whose
            // surrounding bytes fall outside the known marker sets.
            //
            // Key filter: only start a candidate where the byte immediately before
            // does NOT decode to a letter.  A letter before the start means we are
            // mid-word (a suffix of a longer name), so we skip.  Non-letter pre-bytes
            // (space, backslash, non-printables, type/structural bytes) are the normal
            // separator that precedes a genuine name field.
            int headerSize = fileData.length % SLOT_SIZE;
            int numSlots = (fileData.length - headerSize) / SLOT_SIZE;

            List<CharacterRecord> slotCandidates = new ArrayList<>();
            for (int slotIdx = 0; slotIdx < numSlots; slotIdx++) {
                int slotStart = headerSize + slotIdx * SLOT_SIZE;
                int slotEnd = slotStart + SLOT_SIZE;

                for (int off = slotStart + 4; off < slotEnd - 64; off++) {
                    // If the byte before this position decodes to a letter, we are
                    // mid-name — skip to avoid suffix false-positives.
                    int prevDecoded = (fileData[off - 1] & 0xFF) ^ XOR_KEY;
                    if (isValidNameStart(prevDecoded)) continue;

                    int c0 = (fileData[off] & 0xFF) ^ XOR_KEY;
                    if (!isValidNameStart(c0)) continue;

                    int len = 0;
                    while (len < MAX_NAME_LEN && off + len < slotEnd) {
                        int c = (fileData[off + len] & 0xFF) ^ XOR_KEY;
                        if (c == 0 || !isValidNameChar(c)) break;
                        len++;
                    }
                    if (len < 4) continue;

                    int pad = 0;
                    int padPos = off + len;
                    while (padPos + pad < fileData.length && pad < 30
                            && (fileData[padPos + pad] & 0xFF) == XOR_KEY) {
                        pad++;
                    }
                    if (pad < 15) continue;

                    String name = decode(fileData, off, len).trim();
                    if (!name.isEmpty() && !isStructuralFill(name)) {
                        slotCandidates.add(new CharacterRecord(name, off, 0));
                    }
                }
            }

            // When two candidates end at the same position, prefer the SHORTER one:
            // the longer one has a spurious prefix (e.g. "E Priesterin" → keep "Priesterin").
            List<CharacterRecord> filteredSlot = new ArrayList<>();
            for (CharacterRecord cand : slotCandidates) {
                int candEnd = cand.offset + cand.name.length();
                boolean hasShorterAtSameEnd = false;
                for (CharacterRecord other : slotCandidates) {
                    if (other != cand
                            && other.offset > cand.offset
                            && (other.offset + other.name.length()) == candEnd) {
                        hasShorterAtSameEnd = true;
                        break;
                    }
                }
                if (!hasShorterAtSameEnd) {
                    filteredSlot.add(cand);
                }
            }

            // Merge: add slot-found characters not already covered by marker scan
            for (CharacterRecord slotChar : filteredSlot) {
                boolean alreadyFound = false;
                for (CharacterRecord existing : characterRecords) {
                    if (Math.abs(existing.offset - slotChar.offset) < 32) {
                        alreadyFound = true;
                        break;
                    }
                }
                if (!alreadyFound) {
                    characterRecords.add(slotChar);
                }
            }

            // Sort by offset
            characterRecords.sort((a, b) -> Integer.compare(a.offset, b.offset));

            DefaultListModel<CharacterRecord> model = new DefaultListModel<>();
            for (CharacterRecord r : characterRecords) model.addElement(r);
            characterList.setModel(model);
            countLabel.setText("Characters: " + characterRecords.size());
            statusLabel.setText("Loaded: " + path);

            Preferences prefs = Preferences.userNodeForPackage(Main.class);
            prefs.put(PREF_LAST_FILE, path.toAbsolutePath().toString());

        } catch (IOException e) {
            statusLabel.setText("Error loading file: " + e.getMessage());
        }
    }

    private void checkAndAddName(int offset, boolean collectAll, List<CharacterRecord> collector) {
        if (offset <= 1 || offset >= fileData.length) return;

        int marker1 = fileData[offset - 1] & 0xFF;
        if (marker1 != 0xAF && marker1 != 0xE0 && marker1 != 0xF6 && marker1 != 0xF7 && marker1 != 0xF1 && marker1 != 0xAE) return;

        int marker2 = fileData[offset - 2] & 0xFF;
        if (marker2 != 0xB4 && marker2 != 0xB3 && marker2 != 0xB7 && marker2 != 0xB5 && marker2 != 0xB6 && marker2 != 0x1A && marker2 != 0x17 && marker2 != 0x13 && marker2 != 0x19 && marker2 != 0xA2 && marker2 != 0xB0 && marker2 != 0xE1 && marker2 != 0xAD) return;

        int firstChar = (fileData[offset] & 0xFF) ^ XOR_KEY;
        if (firstChar < 32) return;

        if (firstChar == '7' || firstChar == '\\') return;

        // If first char is non-letter printable ASCII (like '|', '%', '>') followed by a letter
        // or space, the marker scan started 1-2 bytes early (the pre-byte was the true marker).
        // Advance offset to the first real letter so the name is stored without the prefix.
        if (!isValidNameStart(firstChar) && firstChar != ' ' && firstChar != '-' && firstChar != '\'') {
            // Advance past up to 2 non-letter prefix chars to find the real name start
            int skip = 0;
            while (skip < 3 && offset + skip < fileData.length) {
                int c = (fileData[offset + skip] & 0xFF) ^ XOR_KEY;
                if (isValidNameStart(c)) break;
                skip++;
            }
            if (skip == 0 || skip >= 3) return; // no improvement possible
            offset += skip;
            firstChar = (fileData[offset] & 0xFF) ^ XOR_KEY;
        }

        // Metadata blocks start with repeated D's (DDDD...). Real names starting with D
        // always have a different second character (e.g. "Diebin", "DIEBINA", "Dramian").
        if (firstChar == 'D' && offset + 1 < fileData.length) {
            int secondChar = (fileData[offset + 1] & 0xFF) ^ XOR_KEY;
            if (secondChar == 'D') return;
        }

        if (firstChar == 'P') {
            if (offset + 2 < fileData.length) {
                int secondChar = (fileData[offset + 1] & 0xFF) ^ XOR_KEY;
                int thirdChar = (fileData[offset + 2] & 0xFF) ^ XOR_KEY;
                if (secondChar == '7' && thirdChar == '\\') return;
            }
        }

        int len = 0;
        while (len < MAX_NAME_LEN && offset + len < fileData.length) {
            int b = fileData[offset + len] & 0xFF;
            int val = b ^ XOR_KEY;
            if (val == 0) break;
            if (val < 32 || val == 0x7F || (val >= 0x80 && val <= 0x9F)) break;
            len++;
        }

        if (len == 0) return;

        int paddingCount = 0;
        int pos = offset + len + 4; // skip up to 4 class/type bytes that may follow name before padding
        while (pos < fileData.length && paddingCount < 25) {
            if ((fileData[pos] & 0xFF) == XOR_KEY) {
                paddingCount++;
                pos++;
            } else {
                break;
            }
        }

        if (paddingCount >= 15) {
            String name = decode(fileData, offset, len).trim();
            if (name.isEmpty()) return;
            collector.add(new CharacterRecord(name, offset, marker1));
        }
    }

    private void searchByName() {
        String searchText = searchField.getText().trim();
        if (searchText.isEmpty() || fileData == null) return;

        byte[] encoded = new byte[searchText.length()];
        for (int i = 0; i < searchText.length(); i++) {
            encoded[i] = (byte) ((searchText.charAt(i) & 0xFF) ^ XOR_KEY);
        }

        int searchOffset = 0;
        boolean found = false;
        while (true) {
            int idx = indexOf(fileData, encoded, searchOffset);
            if (idx < 0) break;
            
            if (idx >= 2) {
                int m1 = fileData[idx - 1] & 0xFF;
                int m2 = fileData[idx - 2] & 0xFF;
                if ((m1 == 0xAF || m1 == 0xE0 || m1 == 0xF6 || m1 == 0xF7 || m1 == 0xF1 || m1 == 0xAE) &&
                    (m2 == 0xB4 || m2 == 0xB3 || m2 == 0xB7 || m2 == 0xB5 || m2 == 0xB6 || m2 == 0x1A || m2 == 0x17 || m2 == 0x13 || m2 == 0x19 || m2 == 0xA2 || m2 == 0xB0 || m2 == 0xE1 || m2 == 0xAD)) {
                    
                    int pad = 0;
                    int p = idx + searchText.length() + 4; // skip up to 4 class/type bytes before padding
                    while (p < fileData.length && fileData[p] == (byte) XOR_KEY && pad < 25) {
                        pad++;
                        p++;
                    }
                    
                    if (pad >= 15) {
                        String fullName = decode(fileData, idx, searchText.length()).trim();
                        CharacterRecord existingRecord = null;
                        for (CharacterRecord r : characterRecords) {
                            if (r.offset == idx) {
                                existingRecord = r;
                                break;
                            }
                        }
                        if (existingRecord == null) {
                            existingRecord = new CharacterRecord(fullName, idx, m1);
                            characterRecords.add(existingRecord);
                            characterRecords.sort((a, b) -> Integer.compare(a.offset, b.offset));
                            DefaultListModel<CharacterRecord> model = new DefaultListModel<>();
                            for (CharacterRecord r : characterRecords) model.addElement(r);
                            characterList.setModel(model);
                            countLabel.setText("Characters: " + characterRecords.size());
                        }
                        characterList.setSelectedValue(existingRecord, true);
                        statusLabel.setText("Found: " + fullName + " at 0x" + Integer.toHexString(idx).toUpperCase());
                        found = true;
                        break;
                    }
                }
            }
            searchOffset = idx + 1;
        }

        if (!found) {
            statusLabel.setText("Name '" + searchText + "' not found in file.");
        }
    }

    private static int indexOf(byte[] data, byte[] pattern, int startOffset) {
        for (int i = startOffset; i <= data.length - pattern.length; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private void performRename() {
        CharacterRecord selected = characterList.getSelectedValue();
        String newName = nameField.getText();
        if (selected == null || newName.isEmpty()) return;

        try {
            long timestamp = System.currentTimeMillis() / 1000L;
            Path backup = currentLplPath.resolveSibling(currentLplPath.getFileName().toString() + ".bak." + timestamp);
            Files.copy(currentLplPath, backup, StandardCopyOption.REPLACE_EXISTING);

            byte[] encoded = encode(newName);
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
        return new String(decoded, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private byte[] encode(String name) {
        byte[] bytes = name.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        byte[] encoded = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            encoded[i] = (byte) ((bytes[i] & 0xFF) ^ XOR_KEY);
        }
        return encoded;
    }

    private static class CharacterRecord {
        String name;
        int offset;
        int marker1;

        CharacterRecord(String name, int offset, int marker1) {
            this.name = name;
            this.offset = offset;
            this.marker1 = marker1;
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
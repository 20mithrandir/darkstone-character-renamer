### Darkstone Character Renamer

This tool is designed to rename characters in Darkstone save files (`characters.lpl`).

#### Character Name Encoding
Character names in Darkstone are not stored as plain text. Instead, they are obfuscated using a simple **XOR cipher**.

- **XOR Key:** `0xEB`
- **Encoding:** To encode a name, each byte of the plain text string is XORed with `0xEB`.
- **Decoding:** Since XOR is its own inverse, decoding uses the same process: each byte from the save file is XORed with `0xEB` to retrieve the original character.
- **Null Terminator/Padding:** In the save file, a null character (`\0`) is represented by the byte `0xEB` (since `0 ^ 0xEB = 0xEB`).

#### Identifying Character Sections
The tool identifies character names by performing a linear scan of the `characters.lpl` file and applying several filters and heuristics to distinguish genuine names from metadata or other binary data.

1.  **Structural Markers:** Valid character slots are consistently preceded by specific byte sequences:
    - **Primary Marker:** The byte immediately before the name is typically `0xAF`, `0xE0`, `0xF6`, `0xF7`, or `0xF1`.
    - **Secondary Marker:** The byte at `offset - 2` must match one of several structural markers (e.g., `0xB4`, `0xB3`, `0xB7`, `0xB5`, `0xB6`, `0x1A`, `0x17`, `0x13`, `0x19`, `0xA2`, `0xB0`, `0xE1`, `0xAD`).
2.  **Character Encoding:** 
    - Names are decoded using the **ISO-8859-1** charset.
    - This allows support for extended ASCII characters, including German umlauts like `ä` and `ö`.
3.  **Printable Character Check:** The first byte of the potential name (after XOR decoding) must be a printable character (ASCII >= 32).
4.  **Metadata Filtering:** Certain printable sequences that appear in metadata headers (e.g., starting with `F`, `7`, `\`, or `D`) are explicitly skipped. A specific check is also performed for the sequence `P7\` to avoid metadata false positives.
5.  **Padding Heuristic:** Genuine character names are followed by significant padding of `0xEB` bytes (which represents XORed nulls). The tool expects at least 15 bytes of `0xEB` padding.
6.  **De-duplication:** The tool ensures that identified offsets are at least 32 bytes apart to avoid partial or duplicate name detection.

#### Persistence ("Sticky" Files)
The tool remembers the last successfully opened save file using system preferences. Upon restart, it automatically attempts to load the previous file, skipping the automatic scan if successful. The file chooser also defaults to the directory of the currently loaded file.

#### Renaming Logic
When a name is changed, the tool performs the following steps:

1.  **Backup:** A timestamped backup of the original `characters.lpl` is created.
2.  **Encoding:** The new name is XOR-encoded with `0xEB`.
3.  **Buffer Writing:**
    - Darkstone uses a fixed-size buffer for character names, approximately **60 bytes** long.
    - The tool writes the encoded name starting at the detected offset.
    - Any remaining space in the 60-byte buffer is filled with the padding byte `0xEB` to ensure the structure of the save file remains intact and no garbage data is left behind.
4.  **Persistence:** The modified byte array is written back to the `characters.lpl` file.

# Darkstone `.lpl` File Format — Character Analysis

## File Structure

```
[ HEADER  (variable, header_size = file_size % 0x65E0) ]
[ SLOT 0  (0x65E0 = 26080 bytes)                       ]
[ SLOT 1  (0x65E0 bytes)                               ]
[ ...                                                  ]
[ SLOT N-1                                             ]
```

| Property       | Value                                             |
|----------------|---------------------------------------------------|
| Slot size      | `0x65E0` (26080 bytes) — universal across formats |
| XOR key        | `0xEB`                                            |
| Header size    | `file_size % 0x65E0`                              |
| Slot count     | `(file_size − header_size) / 0x65E0`              |

### Known file variants

| Source             | File size    | Header   | Slots |
|--------------------|-------------|----------|-------|
| New (single-type)  | `0x13E6D4`  | `0x0114` | 50    |
| New (combined .16) | `0x13E744`  | `0x0184` | 50    |
| Restoration        | `0x1714E4`  | `0x0024` | 58    |
| Carnivoredude      | `0x353BEC`  | `0x4E8C` | 133   |

---

## Character Slot Layout

Each 26080-byte slot contains exactly one character record. Within the slot:

```
[ ... binary data ... ]
[ type byte (1 B)     ]   ← at name_offset − 4
[ 3 structural bytes  ]   ← at name_offset − 3, −2, −1
[ NAME BUFFER (60 B)  ]   ← name_offset: XOR-decoded name, then 0xEB fill
[ ... binary data ... ]
```

### Type byte values (at `name_offset − 4`)

| Value  | Class              |
|--------|--------------------|
| `0x24` | Warrior (Krieger)  |
| `0x28` | Mage (Magier)      |
| `0xBB` | Thief (Meuchler/Diebin) |
| `0x00` | Monk (Mönch/Priesterin) |

### Pre-bytes pattern (bytes immediately before the name)

```
[name_offset − 3] [name_offset − 2] [name_offset − 1] → [name_offset]
     0xDD               0xB7               0xAF         → NAME start (Warrior/Mage/Monk)
     0xDC               0xB7               0xAF         → NAME start (Thief — byte −3 differs)
```

Byte `−4` (the type byte) varies per class; bytes `−3`, `−2`, `−1` are constant within the class family.

---

## Character Positions — Single-Type Example Files (`examples/new/`, header `0x114`)

Binary analysis of the eight single-type `.lpl` files. Each file has 50 slots; the two characters
of the same type occupy consecutive slots starting at slot 0 (or slot 1 for Mage-M/Thief-M).

| Class      | Gender | Name        | Slot | In-Slot   | Type byte | Pre [−1, −2, −3]   | Pad |
|------------|--------|-------------|------|-----------|-----------|--------------------|-----|
| Warrior    | M      | KRIEGERA    | 0    | `0x0056`  | `0x24`    | AF, B7, DD         | 56  |
| Warrior    | M      | KRIEGERB    | 1    | `0x0056`  | `0x24`    | AF, B7, DD         | 56  |
| Warrior    | F      | KRIEGERINA  | 0    | `0x0016`  | `0x24`    | AF, B7, DD         | 54  |
| Warrior    | F      | KRIEGERINB  | 1    | `0x0016`  | `0x24`    | AF, B7, DD         | 54  |
| Mage       | M      | MAGIERA     | 0    | `0x6578`  | `0x28`    | AF, B7, DD         | 57  |
| Mage       | M      | MAGIERB     | 1    | `0x6578`  | `0x28`    | AF, B7, DD         | 57  |
| Mage       | F      | MAGIERINA   | 0    | `0x0016`  | `0x28`    | AF, B7, DD         | 55  |
| Mage       | F      | MAGIERINB   | 1    | `0x0478`  | `0x28`    | AF, B7, DD         | 55  |
| Thief      | M      | MEUCHLERA   | 0    | `0x6564`  | `0xBB`    | AF, B7, DC         | 55  |
| Thief      | M      | MEUCHLERB   | 1    | `0x6564`  | `0xBB`    | AF, B7, DC         | 55  |
| Thief      | F      | DIEBINA     | 0    | `0x003E`  | `0xBB`    | AF, B7, DC         | 57  |
| Thief      | F      | DIEBINB     | 1    | `0x003E`  | `0xBB`    | AF, B7, DC         | 57  |
| Monk       | M      | MOENCHA     | 0    | `0x0446`  | `0x00`    | AF, B7, DD         | 57  |
| Monk       | M      | MOENCHB     | 1    | `0x0446`  | `0x00`    | AF, B7, DD         | 57  |
| Monk       | F      | PRIESTERIA  | 0    | `0x0016`  | `0x00`    | AF, B7, DD         | 54  |
| Monk       | F      | PRIESTERIB  | 1    | `0x0016`  | `0x00`    | AF, B7, DD         | 54  |

**Notes:**
- `In-Slot` = byte offset of name start within the 26080-byte slot (= `file_offset − header − slot_index × 0x65E0`)
- Warrior-F, Mage-F (first slot), and Monk-F all share `in_slot=0x0016` — they use the same compact layout
- Mage-F second instance uses `in_slot=0x0478` — the game assigned it to a mage-specific slot bank
- Mage-M and Thief-M names appear near the **end** of their slot (`0x6578`, `0x6564`) — different struct layout
- Thief family uses `0xDC` at `name−3`; all other classes use `0xDD`

---

## Combined File Analysis (`examples/new/characters.lpl.16`, header `0x184`)

All 16 test characters. Header is larger (0x184 vs 0x114), so `in_slot` values differ from
single-type files. Marker-scan found 10/16; slot-scan recovered all 16.

| Class    | Gender | Name        | Slot | In-Slot   | Detected by   |
|----------|--------|-------------|------|-----------|---------------|
| Warrior  | M      | KRIEGERA    | 0    | `0x65F6`  | slot scan     |
| Warrior  | M      | KRIEGERB    | 0    | `0x65D6`  | marker scan   |
| Warrior  | F      | KRIEGERINA  | 1    | `0x65D6`  | marker scan   |
| Warrior  | F      | KRIEGERINB  | 2    | `0x65D6`  | marker scan   |
| Mage     | M      | MAGIERA     | 4    | `0x1888`  | slot scan     |
| Mage     | M      | MAGIERB     | 5    | `0x1888`  | slot scan     |
| Mage     | F      | MAGIERINA   | 6    | `0x0408`  | marker scan   |
| Mage     | F      | MAGIERINB   | 7    | `0x0408`  | marker scan   |
| Thief    | M      | MEUCHLERA   | 8    | `0x0408`  | marker scan   |
| Thief    | M      | MEUCHLERB   | 9    | `0x0408`  | marker scan   |
| Thief    | F      | DIEBINA     | 10   | `0x17C6`  | slot scan     |
| Thief    | F      | DIEBINB     | 11   | `0x17C6`  | slot scan     |
| Monk     | M      | MOENCHA     | 12   | `0x17C6`  | slot scan     |
| Monk     | M      | MOENCHB     | 13   | `0x17C6`  | slot scan     |
| Monk     | F      | PRIESTERIA  | 14   | `0x6506`  | marker scan   |
| Monk     | F      | PRIESTERIB  | 15   | `0x6506`  | marker scan   |

**Key difference from single-type files:** Larger header (0x184) shifts all `in_slot` values.
Warriors (M) and some classes now appear near slot **end** (`0x65D6`/`0x65F6`).
Mage-M, Thief-F, Monk-M land at `0x1888`/`0x17C6` — unrecognised pre-bytes, only found by slot scan.

---

## Restoration Format (`examples/restoration/`, header `0x0024`)

Custom-named characters using the Restoration game version. Header is only 36 bytes.
All 12 characters per file are detected correctly by the existing marker-based scan.

| File             | Characters (from names file)                                            |
|------------------|-------------------------------------------------------------------------|
| `characters.lpl` | ConanA, ConanB, SonjaA, SonjaB, MerlinA, MerlinB, YenneferA, YenneferB, DerrickA, DerrickB, LisbethA, LisbethB |
| `characters2.lpl`| Same, with LisbethB renamed to "Lisbeth die Große"                      |

---

## Carnivoredude (`examples/new/characters.lpl.carnivoredude`, header `0x4E8C`)

Large real-world save (133 slots). Uses completely different `in_slot` positions from the example files,
consistent with a different game version or patch level.

**Detected `in_slot` clusters:**

| Cluster | Approx. range         | Examples                              |
|---------|-----------------------|---------------------------------------|
| A       | `0x13AC` – `0x1400`  | Amazone, Diebin, Krieger, Zauberer    |
| B       | `0x17F9` – `0x1810`  | Aaaaaaaa-type warriors, Krieger, Mönch|
| C       | `0x187E` – `0x1880`  | Amazone, Hexe, Räuber                 |
| D       | `0x1C9E` – `0x1CDB`  | Räuber, Mönch, Diebin                 |

**Why characters are missed by marker scan:** The pre-bytes (`name−1`, `name−2`) in carnivoredude
do not match the known marker sets derived from the structured example files. The game version
used to create this save uses different structural byte values at those positions.

**Clean-name scan result:** 38 unique names found (vs ~28 by marker scan) — the slot-based
approach recovers the ~10 missing characters without requiring any pre-byte patterns.

---

## Detection Algorithm (3-pass)

### Pass 1 — Marker scan (full file including header)

Scans every byte for the two-byte pre-marker pattern that precedes known name fields:

```
marker1 (at name−1): 0xAF | 0xE0 | 0xF6 | 0xF7 | 0xF1 | 0xAE
marker2 (at name−2): 0xB4 | 0xB3 | 0xB7 | 0xB5 | 0xB6 | 0x1A | 0x17 | 0x13 |
                     0x19 | 0xA2 | 0xB0 | 0xE1 | 0xAD
```

Accepts name if: first decoded char ≥ 0x20 (not filtered), `len > 0`, and ≥15 `0xEB`
padding bytes follow (offset by up to 4 type/class bytes).

### Pass 2 — Proximity dedup

Drops candidates within 32 bytes of an already-accepted candidate.

### Pass 3 — Slot scan (slots only, not header)

Catches characters whose pre-bytes fall outside the marker sets above (e.g. carnivoredude
version, or the Mage-M/Thief-F/Monk-M positions in the `.16` file).

```
header_size = file_size % SLOT_SIZE          // derive header from file geometry
num_slots   = (file_size - header_size) / SLOT_SIZE

for each slot_index in [0, num_slots):
    slot_start = header_size + slot_index × SLOT_SIZE

    for each offset in [slot_start + 4, slot_end − 64):
        prev = file_data[offset − 1] ^ 0xEB
        if prev is a letter (A–Z, a–z, umlaut): skip   ← avoids mid-name starts

        c0 = file_data[offset] ^ 0xEB
        if c0 is NOT a valid name-start letter: continue

        scan while chars are valid name chars (letters + space/hyphen/apostrophe)
        count 0xEB padding bytes after name

        if name_length >= 4 AND padding >= 15
           AND NOT isStructuralFill(name):
            record candidate

apply same-end dedup: drop longer candidate when shorter ends at same position
merge: add candidates not within 32 bytes of any marker-scan result
```

**`isStructuralFill` rejects:**
- Names ending in `"DD"` — the XOR-decoded form of `0xAF 0xAF` (two consecutive marker1 bytes
  that appear as pre-bytes before empty name buffers in unused slots)
- Names where the longest run of one character is ≥6 AND covers >50% of the name length
  (e.g. "DDDDDDDDDDDD" from 0xAF fill bytes in unused slot space)

**Valid name-start characters:** `A–Z`, `a–z`, `Ä Ö Ü ä ö ü ß`  
**Valid name body characters:** same as above, plus ` ` (space), `-`, `'`

### Verified results across all known file variants

| File                        | Expected | Found | Method           |
|-----------------------------|----------|-------|------------------|
| `characters.lpl.kriegerab`  | 2        | 2     | marker           |
| `characters.lpl.diebinab`   | 2        | 2     | marker + slot    |
| `characters.lpl.magierab`   | 2        | 2     | marker           |
| `characters.lpl.moenchab`   | 2        | 2     | marker + slot    |
| `characters.lpl.16`         | 16       | 16    | marker + slot    |
| `restoration/characters.lpl`| 12       | 12    | marker           |
| `restoration/characters2.lpl`| 12      | 12    | marker           |
| `characters.lpl.peter`      | 12       | 12    | marker           |
| `characters.lpl.carnivoredude` | ~28   | 28    | marker + slot    |

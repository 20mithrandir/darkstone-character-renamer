import os
import shutil
import time

# ==========================================================
# DEIN FORMULAR - HIER DEINE ÄNDERUNGEN EINTRAGEN
# ==========================================================
ALTEN_NAMEN_EINTRAGEN = "AA"
NEUEN_NAMEN_EINTRAGEN  = " SuperHeld "
# ==========================================================

def xor_crypt(data):
    return bytes([b ^ 0xEB for b in data])

def start_rename():
    filename = "characters.lpl"
    
    if not os.path.exists(filename):
        print("Fehler: Datei 'characters.lpl' nicht im gleichen Ordner gefunden!")
        input("\nDrücke Enter zum Beenden...")
        return

    # Backup zur Sicherheit
    shutil.copy(filename, f"backup_{int(time.time())}.lpl")
    
    with open(filename, "r+b") as f:
        data = bytearray(f.read())
        
        # Codierung
        alt_bytes = xor_crypt(ALTEN_NAMEN_EINTRAGEN.encode('iso-8859-1').ljust(60, b'\x00'))
        neu_bytes = xor_crypt(NEUEN_NAMEN_EINTRAGEN.encode('iso-8859-1').ljust(60, b'\x00'))
        
        pos = data.find(alt_bytes)
        
        if pos != -1:
            data[pos:pos+60] = neu_bytes
            f.seek(0)
            f.write(data)
            print(f"Erfolg! '{ALTEN_NAMEN_EINTRAGEN}' wurde zu '{NEUEN_NAMEN_EINTRAGEN}' geändert.")
        else:
            print(f"Fehler: Konnte den Namen '{ALTEN_NAMEN_EINTRAGEN}' nicht finden.")
            print("Tipp: Achte auf Groß-/Kleinschreibung.")

    input("\nFertig. Drücke Enter zum Beenden...")

if __name__ == "__main__":
    start_rename()
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Quick smoke-test for the 3-pass detection logic without launching the GUI. */
public class TestScan {
    private static final int XOR_KEY  = 0xEB;
    private static final int MAX_NAME = 50;
    private static final int SLOT_SIZE = 0x65E0;

    static class Rec {
        String name; int offset; int marker1;
        Rec(String n, int o, int m) { name=n; offset=o; marker1=m; }
        public String toString() { return String.format("%-30s 0x%08X", name, offset); }
    }

    public static void main(String[] args) throws IOException {
        String[] files = {
            "examples/new/characters.lpl.kriegerab",
            "examples/new/characters.lpl.diebinab",
            "examples/new/characters.lpl.magierab",
            "examples/new/characters.lpl.moenchab",
            "examples/new/characters.lpl.16",
            "examples/restoration/characters.lpl",
            "examples/restoration/characters2.lpl",
            "examples/new/characters.lpl.peter",
            "examples/new/characters.lpl.carnivoredude"
        };
        for (String f : files) {
            System.out.println("\n=== " + f);
            List<Rec> found = scan(Files.readAllBytes(Path.of(f)));
            System.out.println("  TOTAL: " + found.size());
            for (Rec r : found) System.out.println("  " + r);
        }
    }

    private static List<Rec> scan(byte[] data) {
        // PASS 1 – marker scan
        List<Rec> all = new ArrayList<>();
        for (int i = 2; i < data.length - MAX_NAME; i++) checkAndAdd(data, i, all);

        // PASS 1b – same-end dedup (keep shorter with primary marker)
        List<Rec> pass1 = new ArrayList<>();
        for (Rec c : all) {
            int cEnd = c.offset + c.name.length();
            boolean drop = false;
            for (Rec o : all) {
                if (o.offset > c.offset && o.offset < cEnd
                        && (o.offset + o.name.length()) == cEnd
                        && o.marker1 != 0xAE) { drop = true; break; }
            }
            if (!drop) pass1.add(c);
        }

        // PASS 2 – proximity dedup
        List<Rec> accepted = new ArrayList<>();
        for (Rec c : pass1) {
            boolean conflict = false;
            for (Rec a : accepted) { if (Math.abs(a.offset - c.offset) < 32) { conflict=true; break; } }
            if (!conflict) accepted.add(c);
        }

        // PASS 3 – slot scan
        int hdr   = data.length % SLOT_SIZE;
        int slots = (data.length - hdr) / SLOT_SIZE;
        List<Rec> slotRaw = new ArrayList<>();
        for (int si = 0; si < slots; si++) {
            int sStart = hdr + si * SLOT_SIZE;
            int sEnd   = sStart + SLOT_SIZE;
            for (int off = sStart + 4; off < sEnd - 64; off++) {
                int prev = (data[off-1] & 0xFF) ^ XOR_KEY;
                if (isNameStart(prev)) continue;
                int c0 = (data[off] & 0xFF) ^ XOR_KEY;
                if (!isNameStart(c0)) continue;
                int len = 0;
                while (len < MAX_NAME && off+len < sEnd) {
                    int c = (data[off+len] & 0xFF) ^ XOR_KEY;
                    if (c == 0 || !isNameChar(c)) break;
                    len++;
                }
                if (len < 4) continue;
                int pad = 0;
                while (off+len+pad < data.length && pad < 30 && (data[off+len+pad] & 0xFF) == XOR_KEY) pad++;
                if (pad < 15) continue;
                String name = decode(data, off, len).trim();
                if (!name.isEmpty() && !isStructuralFill(name)) slotRaw.add(new Rec(name, off, 0));
            }
        }

        // PASS 3b – slot same-end dedup
        List<Rec> slotFilt = new ArrayList<>();
        for (Rec c : slotRaw) {
            int cEnd = c.offset + c.name.length();
            boolean drop = false;
            for (Rec o : slotRaw) {
                if (o != c && o.offset > c.offset && (o.offset + o.name.length()) == cEnd) { drop=true; break; }
            }
            if (!drop) slotFilt.add(c);
        }

        // Merge new slot candidates
        for (Rec sc : slotFilt) {
            boolean known = false;
            for (Rec a : accepted) { if (Math.abs(a.offset - sc.offset) < 32) { known=true; break; } }
            if (!known) accepted.add(sc);
        }

        accepted.sort((a,b) -> Integer.compare(a.offset, b.offset));
        return accepted;
    }

    private static void checkAndAdd(byte[] data, int off, List<Rec> out) {
        if (off <= 1 || off >= data.length) return;
        int m1 = data[off-1] & 0xFF;
        if (m1!=0xAF && m1!=0xE0 && m1!=0xF6 && m1!=0xF7 && m1!=0xF1 && m1!=0xAE) return;
        int m2 = data[off-2] & 0xFF;
        if (m2!=0xB4 && m2!=0xB3 && m2!=0xB7 && m2!=0xB5 && m2!=0xB6 && m2!=0x1A
         && m2!=0x17 && m2!=0x13 && m2!=0x19 && m2!=0xA2 && m2!=0xB0 && m2!=0xE1 && m2!=0xAD) return;
        int fc = (data[off] & 0xFF) ^ XOR_KEY;
        if (fc < 32 || fc == '7' || fc == '\\') return;
        if (fc == 'D' && off+1 < data.length && ((data[off+1]&0xFF)^XOR_KEY) == 'D') return;
        if (fc == 'P' && off+2 < data.length && ((data[off+1]&0xFF)^XOR_KEY) == '7'
                      && ((data[off+2]&0xFF)^XOR_KEY) == '\\') return;
        int len = 0;
        while (len < MAX_NAME && off+len < data.length) {
            int v = (data[off+len]&0xFF)^XOR_KEY;
            if (v==0 || v<32 || v==0x7F || (v>=0x80 && v<=0x9F)) break;
            len++;
        }
        if (len == 0) return;
        int pad = 0; int pos = off+len+4;
        while (pos < data.length && pad < 25 && (data[pos]&0xFF)==XOR_KEY) { pad++; pos++; }
        if (pad < 15) return;
        String name = decode(data, off, len).trim();
        if (!name.isEmpty()) out.add(new Rec(name, off, m1));
    }

    private static boolean isNameStart(int c) {
        return (c>='A'&&c<='Z')||(c>='a'&&c<='z')
            ||c==0xC4||c==0xD6||c==0xDC||c==0xE4||c==0xF6||c==0xFC||c==0xDF;
    }
    private static boolean isNameChar(int c) {
        return isNameStart(c)||c==' '||c=='-'||c=='\'';
    }
    private static boolean isStructuralFill(String s) {
        if (s.endsWith("DD")) return true;
        if (s.length() < 3) return false;
        int maxRun=1, run=1;
        for (int i=1;i<s.length();i++) {
            if (s.charAt(i)==s.charAt(i-1)) { run++; if(run>maxRun) maxRun=run; } else run=1;
        }
        return maxRun>=6 && (maxRun*100/s.length())>50;
    }
    private static String decode(byte[] data, int off, int len) {
        byte[] b = new byte[len];
        for (int i=0;i<len;i++) b[i]=(byte)((data[off+i]&0xFF)^XOR_KEY);
        return new String(b, java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}

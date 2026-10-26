package gg.nocturnum.hud.util;

/** Display names for GLFW key codes (same on every game version). */
public final class KeyNames {
    private KeyNames() {}

    public static String of(int key) {
        if (key < 0) return "—";
        if (key >= 65 && key <= 90) return String.valueOf((char) key);
        if (key >= 48 && key <= 57) return String.valueOf((char) key);
        if (key >= 290 && key <= 314) return "F" + (key - 289);
        if (key >= 320 && key <= 329) return "Num" + (key - 320);
        return switch (key) {
            case 32 -> "Space";
            case 39 -> "'";
            case 44 -> ",";
            case 45 -> "-";
            case 46 -> ".";
            case 47 -> "/";
            case 59 -> ";";
            case 61 -> "=";
            case 91 -> "[";
            case 92 -> "\\";
            case 93 -> "]";
            case 96 -> "`";
            case 256 -> "Esc";
            case 257 -> "Enter";
            case 258 -> "Tab";
            case 259 -> "Backspace";
            case 260 -> "Insert";
            case 261 -> "Delete";
            case 262 -> "→";
            case 263 -> "←";
            case 264 -> "↓";
            case 265 -> "↑";
            case 266 -> "PgUp";
            case 267 -> "PgDn";
            case 268 -> "Home";
            case 269 -> "End";
            case 280 -> "Caps";
            case 340 -> "L-Shift";
            case 341 -> "L-Ctrl";
            case 342 -> "L-Alt";
            case 344 -> "R-Shift";
            case 345 -> "R-Ctrl";
            case 346 -> "R-Alt";
            default -> "Key " + key;
        };
    }
}

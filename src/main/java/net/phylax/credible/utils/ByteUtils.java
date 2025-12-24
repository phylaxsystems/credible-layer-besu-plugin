package net.phylax.credible.utils;

/**
 * Utility class for byte array operations, primarily for logging and debugging.
 */
public final class ByteUtils {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private ByteUtils() {
        // Prevent instantiation
    }

    /**
     * Convert a byte array to a hex string with "0x" prefix.
     * Returns "null" if the input is null, "0x" if empty.
     *
     * @param bytes the byte array to convert
     * @return hex string representation (e.g., "0xabcdef1234")
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length == 0) {
            return "0x";
        }
        char[] hexChars = new char[2 + bytes.length * 2];
        hexChars[0] = '0';
        hexChars[1] = 'x';
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[2 + i * 2] = HEX_CHARS[v >>> 4];
            hexChars[2 + i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Convert a byte array to a shortened hex string for logging.
     * Shows first and last few bytes with "..." in between for long arrays.
     * Returns "null" if the input is null, "0x" if empty.
     *
     * @param bytes the byte array to convert
     * @param maxBytes maximum bytes to show before truncating (shows half at start, half at end)
     * @return shortened hex string (e.g., "0xabcd...ef12" for long arrays)
     */
    public static String toHexShort(byte[] bytes, int maxBytes) {
        if (bytes == null) {
            return "null";
        }
        if (bytes.length == 0) {
            return "0x";
        }
        if (bytes.length <= maxBytes) {
            return toHex(bytes);
        }

        int halfBytes = maxBytes / 2;
        StringBuilder sb = new StringBuilder(2 + halfBytes * 2 + 3 + halfBytes * 2);
        sb.append("0x");

        // First half
        for (int i = 0; i < halfBytes; i++) {
            int v = bytes[i] & 0xFF;
            sb.append(HEX_CHARS[v >>> 4]);
            sb.append(HEX_CHARS[v & 0x0F]);
        }

        sb.append("...");

        // Last half
        for (int i = bytes.length - halfBytes; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            sb.append(HEX_CHARS[v >>> 4]);
            sb.append(HEX_CHARS[v & 0x0F]);
        }

        return sb.toString();
    }

    /**
     * Convert a byte array to a shortened hex string for logging.
     * Uses default max of 8 bytes (shows 4 bytes at start and 4 at end).
     *
     * @param bytes the byte array to convert
     * @return shortened hex string (e.g., "0xabcdef12...34567890")
     */
    public static String toHexShort(byte[] bytes) {
        return toHexShort(bytes, 8);
    }
}

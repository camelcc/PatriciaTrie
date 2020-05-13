package com.pt.compress;

import java.util.List;

import static com.pt.compress.EncoderUtils.*;

public class DecoderUtils {
    public static int ROOT_POS = 12;
    public static int NODE_FLAG_ADDR_TYPE = 0xC0;
    public static int NODE_FLAG_MULTI_CHAR = 0x20;
    public static int NODE_FLAG_TERMINAL = 0x10;

    public static int parseHeader(byte[] buffer) {
        if (buffer[0] != (byte)(0xFF & (MAGIC_NUMBER >> 24)) ||
            buffer[1] != (byte)(0xFF & (MAGIC_NUMBER >> 16)) ||
            buffer[2] != (byte)(0xFF & (MAGIC_NUMBER >> 8)) ||
            buffer[3] != (byte)(0xFF & MAGIC_NUMBER)) {
            throw new RuntimeException("invalid header format");
        }
        return ROOT_POS;
    }

    public static int[] contains(StringBuilder sb, byte[] buffer, int bp, char[] chars, int cp) {
        int[] nc = DecoderUtils.readPtNodeCount(buffer, bp);
        int count = nc[0], pos = nc[1];
        for (int i = 0; i < count; i++) {
            // parse ptnode
            byte flag = buffer[pos++];
            // parse chars
            String pts;
            if (!nodeMultipleChars(flag)) {
                char c = (char)(readUnsignedShort(buffer, pos));
                pos += 2;
                pts = String.valueOf(c);
            } else {
                String[] t = readString(buffer, pos);
                pos = Integer.parseInt(t[1]);
                pts = t[0];
            }
            // address
            int childAddrSize = (flag & NODE_FLAG_ADDR_TYPE)>>6;
            if (pts.charAt(0) != chars[cp]) {
                pos += childAddrSize;
                continue;
            }

            // should return childAddr
            int term = nodeIsTerminator(flag) ? 1 : 0;

            int childAddr = nodeChildrenAddressSize(buffer, pos, flag);
            int ptci = 0;
            while (cp < chars.length && ptci < pts.length() && pts.charAt(ptci) == chars[cp]) {
                ptci++;
                cp++;
            }
            if (ptci < pts.length()) {
                if (cp < chars.length) {
                    return new int[]{-1, 0};
                } else {
                    // cp == chars.length
                    sb.append(pts);
                    return new int[]{childAddr, term};
                }
            }
            // ptci == pts.length
            sb.append(pts);
            if (cp == chars.length) {
                return new int[]{childAddr, term};
            }
            // ptci == pts.length && cp < chars.length, continue matching
            if (childAddr == 0) {
                return new int[]{-1, 0};
            }
            return contains(sb, buffer, childAddr, chars, cp);
        }
        return new int[]{-1, 0};
    }

    // dfs, order children by frequency, then collect up to a limit candidates
    public static void traverse(byte[] buffer, int position, StringBuilder sb, List<String> data) {
        // position != 0
        int[] nc = DecoderUtils.readPtNodeCount(buffer, position);
        int count = nc[0], pos = nc[1];
        for (int i = 0; i < count; i++) {
            // parse ptnode
            byte flag = buffer[pos++];
//            boolean terminal = nodeIsTerminator(flag);
            // parse chars
            String pts;
            if (!nodeMultipleChars(flag)) {
                char c = (char)(readUnsignedShort(buffer, pos));
                pos += 2;
                pts = String.valueOf(c);
            } else {
                String[] t = readString(buffer, pos);
                pos = Integer.parseInt(t[1]);
                pts = t[0];
            }
            StringBuilder nsb = new StringBuilder(sb);
            nsb.append(pts);
            if (nodeIsTerminator(flag)) {
                data.add(nsb.toString());
            }
            // address
            int childAddrSize = (flag & NODE_FLAG_ADDR_TYPE)>>6;
            int childAddr = nodeChildrenAddressSize(buffer, pos, flag);
            pos += childAddrSize;
            if (childAddr != 0) {
                traverse(buffer, childAddr, nsb, data);
            }
        }
    }

    /**
     * Reads and returns the PtNode count out of a buffer and forwards the pointer.
     */
    public static int[] readPtNodeCount(final byte[] buffer, int position) {
        int msb = readUnsignedByte(buffer, position);
        if (MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT >= msb) {
            return new int[]{msb, position+1};
        }
        msb = ((MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT & msb) << 8)
                + readUnsignedByte(buffer, position+1);
        return new int[]{msb, position+2};
    }

    /**
     * Reads a string from a DictBuffer. This is the converse of the above method.
     */
    public static String[] readString(byte[] buffer, int position) {
        final StringBuilder s = new StringBuilder();
        int character = readUnsignedByte(buffer, position);
        while (character != PTNODE_CHARACTERS_TERMINATOR) {
            character = readUnsignedShort(buffer, position);
            position += 2;
            s.append((char)character);
            character = readUnsignedByte(buffer, position);
        }
        position++;
        return new String[]{s.toString(), String.valueOf(position)};
    }

    public static int readUnsignedByte(byte[] buffer, int position) {
        return buffer[position] & 0xFF;
    }

    public static int readUnsignedShort(byte[] buffer, int position) {
        final int retval = readUnsignedByte(buffer, position);
        return (retval << 8) + readUnsignedByte(buffer, position+1);
    }

    public static int readUnsignedInt24(byte[] buffer, int position) {
        final int retval = readUnsignedShort(buffer, position);
        return (retval << 8) + readUnsignedByte(buffer, position+2);
    }

    public static int readInt(byte[] buffer, int position) {
        final int retval = readUnsignedShort(buffer, position);
        return (retval << 16) + readUnsignedShort(buffer, position+2);
    }

    public static int nodeChildrenAddressSize(byte[] buffer, int position, byte flag) {
        int size = (flag & NODE_FLAG_ADDR_TYPE)>>6;
        if (size == 0) {
            return 0;
        } else if (size == 1) {
            return readUnsignedByte(buffer, position) + position;
        } else if (size == 2) {
            return position + readUnsignedShort(buffer, position);
        } else if (size == 3) {
            return position + readUnsignedInt24(buffer, position);
        } else {
            throw new RuntimeException("invalid children address");
        }
    }

    public static boolean nodeMultipleChars(byte flag) {
        return (flag & NODE_FLAG_MULTI_CHAR) != 0;
    }

    public static boolean nodeIsTerminator(byte flag) {
        return (flag & NODE_FLAG_TERMINAL) != 0;
    }
}

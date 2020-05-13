package com.pt.compress;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class EncoderUtils {
    public static final int MAGIC_NUMBER = 0x9BC13AFE;
    public static final int MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT = 0x7F; // 127
    // Large PtNode array size field size is 2 bytes.
    public static final int LARGE_PTNODE_ARRAY_SIZE_FIELD_SIZE_FLAG = 0x8000;
    public static final int MAX_PTNODES_IN_A_PT_NODE_ARRAY = 0x7FFF; // 32767

    public static final int PTNODE_FLAGS_SIZE = 1;
    public static final int PTNODE_TERMINATOR_SIZE = 1;
    public static final int PTNODE_FREQUENCY_SIZE = 1;
    public static final int PTNODE_MAX_ADDRESS_SIZE = 3;

    public static final int UINT8_MAX = 0xFF;
    public static final int UINT16_MAX = 0xFFFF;
    public static final int UINT24_MAX = 0xFFFFFF;

    public static final int NO_CHILDREN_ADDRESS = Integer.MIN_VALUE;

    // Arbitrary limit to how much passes we consider address size compression should
    // terminate in. At the time of this writing, our largest dictionary completes
    // compression in five passes.
    // If the number of passes exceeds this number, makedict bails with an exception on
    // suspicion that a bug might be causing an infinite loop.
    private static final int MAX_PASSES = 24;

    static final int FLAG_HAS_MULTIPLE_CHARS = 0x20;

    // These flags are used only in the static dictionary.
    static final int MASK_CHILDREN_ADDRESS_TYPE = 0xC0;
    static final int FLAG_CHILDREN_ADDRESS_TYPE_NOADDRESS = 0x00;
    static final int FLAG_CHILDREN_ADDRESS_TYPE_ONEBYTE = 0x40;
    static final int FLAG_CHILDREN_ADDRESS_TYPE_TWOBYTES = 0x80;
    static final int FLAG_CHILDREN_ADDRESS_TYPE_THREEBYTES = 0xC0;

    static final int FLAG_IS_TERMINAL = 0x10;

    static final int PTNODE_CHARACTERS_TERMINATOR = 0x1F;

    /*
     * File header layout is as follows:
     *
     * v |
     * e | MAGIC_NUMBER + version of the file format, 2 bytes.
     * r |
     * sion
     *
     *
     * h |
     * e | size of the file header, 4bytes
     * a |   including the size of the magic number, and the header size
     * d |
     * ersize
     */
    public static int writeDictionaryHeader(OutputStream dst) throws IOException {
        int version = 2;

        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream(256);

        // The magic number in big-endian order.
        // Magic number for all versions.
        headerBuffer.write((byte) (0xFF & (MAGIC_NUMBER >> 24)));
        headerBuffer.write((byte) (0xFF & (MAGIC_NUMBER >> 16)));
        headerBuffer.write((byte) (0xFF & (MAGIC_NUMBER >> 8)));
        headerBuffer.write((byte) (0xFF & MAGIC_NUMBER));
        // Dictionary version.
        headerBuffer.write((byte) (0xFF & (version >> 8)));
        headerBuffer.write((byte) (0xFF & version));

        // Options flags
        // TODO: Remove this field.
        final int options = 0;
        headerBuffer.write((byte) (0xFF & (options >> 8)));
        headerBuffer.write((byte) (0xFF & options));
        final int headerSizeOffset = headerBuffer.size();
        // Placeholder to be written later with header size.
        for (int i = 0; i < 4; ++i) {
            headerBuffer.write(0);
        }

        // no attributes, no code point array
        final int size = headerBuffer.size();
        final byte[] bytes = headerBuffer.toByteArray();
        // Write out the header size.
        bytes[headerSizeOffset] = (byte) (0xFF & (size >> 24));
        bytes[headerSizeOffset + 1] = (byte) (0xFF & (size >> 16));
        bytes[headerSizeOffset + 2] = (byte) (0xFF & (size >> 8));
        bytes[headerSizeOffset + 3] = (byte) (0xFF & (size >> 0));
        dst.write(bytes);

        headerBuffer.close();
        return size;
    }

    /*
     * Node array (FusionDictionary.PtNodeArray) layout is as follows:
     *
     * n |
     * o | the number of PtNodes, 1 or 2 bytes.
     * d | 1 byte = bbbbbbbb match
     * e |   case 1xxxxxxx => xxxxxxx << 8 + next byte
     * c |   otherwise => bbbbbbbb
     * o |
     * unt
     *
     * n |
     * o | sequence of PtNodes,
     * d | the layout of each PtNode is described below.
     * e |
     * s
     */

    /* Node (FusionDictionary.PtNode) layout is as follows:
     *   | CHILDREN_ADDRESS_TYPE  2 bits, 11          : FLAG_CHILDREN_ADDRESS_TYPE_THREEBYTES
     *   |                                10          : FLAG_CHILDREN_ADDRESS_TYPE_TWOBYTES
     * f |                                01          : FLAG_CHILDREN_ADDRESS_TYPE_ONEBYTE
     * l |                                00          : FLAG_CHILDREN_ADDRESS_TYPE_NOADDRESS
     * a | has several chars ?         1 bit, 1 = yes, 0 = no   : FLAG_HAS_MULTIPLE_CHARS
     * g | has a terminal ?            1 bit, 1 = yes, 0 = no   : FLAG_IS_TERMINAL
     * s | has shortcut targets ?      1 bit, 1 = yes, 0 = no   : FLAG_HAS_SHORTCUT_TARGETS
     *   | has bigrams ?               1 bit, 1 = yes, 0 = no   : FLAG_HAS_BIGRAMS
     *   | is not a word ?             1 bit, 1 = yes, 0 = no   : FLAG_IS_NOT_A_WORD
     *   | is possibly offensive ?     1 bit, 1 = yes, 0 = no   : FLAG_IS_POSSIBLY_OFFENSIVE
     *
     * c | IF FLAG_HAS_MULTIPLE_CHARS
     * h |   char, char, char, char    n * 2 bytes
     * a |   end                       1 byte, = 0
     * r | ELSE
     * s |   char                      2 bytes
     *   | END
     * c |
     * h | children address, CHILDREN_ADDRESS_TYPE bytes
     * i | This address is relative to the position of this field.
     * l |
     * drenaddress
     *
     * Char format is:
     * 1 byte = bbbbbbbb match
     * case 000xxxxx: xxxxx << 8 + next byte
     * else: if 00011111 (= 0x1F) : this is the terminator. This is a relevant choice because
     *       unicode code points range from 0 to 0x10FFFF, so any 3-byte value starting with
     *       00011111 would be outside unicode.
     *
     * This allows for the whole unicode range to be encoded, including chars outside of
     * the BMP. Also everything in the iso-latin-1 charset is only 1 byte, except control
     * characters which should never happen anyway (and still work, but take 3 bytes).
     */

    // This method is responsible for finding a nice ordering of the nodes that favors run-time
    // cache performance and dictionary size.
    public static ArrayList<PatriciaTrie.PtNodeArray> flattenTree(
            final PatriciaTrie pt) {
        final int treeSize = PatriciaTrie.countPtNodes(pt.getRoot().mChildren);
        System.out.println("Counted nodes : " + treeSize);
        final ArrayList<PatriciaTrie.PtNodeArray> flatTree = new ArrayList<>(treeSize);
        return flattenTreeInner(flatTree, pt.getRoot().mChildren);
    }

    private static ArrayList<PatriciaTrie.PtNodeArray> flattenTreeInner(
            final ArrayList<PatriciaTrie.PtNodeArray> list,
            final PatriciaTrie.PtNodeArray ptNodeArray) {
        // Removing the node is necessary if the tails are merged, because we would then
        // add the same node several times when we only want it once. A number of places in
        // the code also depends on any node being only once in the list.
        // Merging tails can only be done if there are no attributes. Searching for attributes
        // in LatinIME code depends on a total breadth-first ordering, which merging tails
        // breaks. If there are no attributes, it should be fine (and reduce the file size)
        // to merge tails, and removing the node from the list would be necessary. However,
        // we don't merge tails because breaking the breadth-first ordering would result in
        // extreme overhead at bigram lookup time (it would make the search function O(n) instead
        // of the current O(log(n)), where n=number of nodes in the dictionary which is pretty
        // high).
        // If no nodes are ever merged, we can't have the same node twice in the list, hence
        // searching for duplicates in unnecessary. It is also very performance consuming,
        // since `list' is an ArrayList so it's an O(n) operation that runs on all nodes, making
        // this simple list.remove operation O(n*n) overall. On Android this overhead is very
        // high.
        // For future reference, the code to remove duplicate is a simple : list.remove(node);
        list.add(ptNodeArray);
        final ArrayList<PatriciaTrie.PtNode> branches = ptNodeArray.mData;
        for (PatriciaTrie.PtNode ptNode : branches) {
            if (null != ptNode.mChildren) flattenTreeInner(list, ptNode.mChildren);
        }
        return list;
    }

    /**
     * Compute the addresses and sizes of an ordered list of PtNode arrays.
     *
     * This method takes a list of PtNode arrays and will update their cached address and size
     * values so that they can be written into a file. It determines the smallest size each of the
     * PtNode arrays can be given the addresses of its children and attributes, and store that into
     * each PtNode.
     * The order of the PtNode is given by the order of the array. This method makes no effort
     * to find a good order; it only mechanically computes the size this order results in.
     *
     * @param flatNodes the ordered list of PtNode arrays
     * @return the same array it was passed. The nodes have been updated for address and size.
     */
    public static ArrayList<PatriciaTrie.PtNodeArray> computeAddresses(final ArrayList<PatriciaTrie.PtNodeArray> flatNodes) {
        // First get the worst possible sizes and offsets
        for (final PatriciaTrie.PtNodeArray n : flatNodes) {
            calculatePtNodeArrayMaximumSize(n);
        }
        final int offset = initializePtNodeArraysCachedAddresses(flatNodes);

        System.out.println("Compressing the array addresses. Original size : " + offset);
        System.out.println("(Recursively seen size : " + offset + ")");

        int passes = 0;
        boolean changesDone = false;
        do {
            changesDone = false;
            int ptNodeArrayStartOffset = 0;
            for (final PatriciaTrie.PtNodeArray ptNodeArray : flatNodes) {
                ptNodeArray.mCachedAddressAfterUpdate = ptNodeArrayStartOffset;
                final int oldNodeArraySize = ptNodeArray.mCachedSize;
                final boolean changed = computeActualPtNodeArraySize(ptNodeArray);
                final int newNodeArraySize = ptNodeArray.mCachedSize;
                if (oldNodeArraySize < newNodeArraySize) {
                    throw new RuntimeException("Increased size ?!");
                }
                ptNodeArrayStartOffset += newNodeArraySize;
                changesDone |= changed;
            }
            updatePtNodeArraysCachedAddresses(flatNodes);
            ++passes;
            if (passes > MAX_PASSES) throw new RuntimeException("Too many passes - probably a bug");
        } while (changesDone);

        final PatriciaTrie.PtNodeArray lastPtNodeArray = flatNodes.get(flatNodes.size() - 1);
        System.out.println("Compression complete in " + passes + " passes.");
        System.out.println("After address compression : "
                + (lastPtNodeArray.mCachedAddressAfterUpdate + lastPtNodeArray.mCachedSize));

        return flatNodes;
    }

    /**
     * Compute the maximum size of each PtNode of a PtNode array, assuming 3-byte addresses for
     * everything, and caches it in the `mCachedSize' member of the nodes; deduce the size of
     * the containing node array, and cache it it its 'mCachedSize' member.
     *
     * @param ptNodeArray the node array to compute the maximum size of.
     */
    private static void calculatePtNodeArrayMaximumSize(
            final PatriciaTrie.PtNodeArray ptNodeArray) {
        int size = getPtNodeCountSize(ptNodeArray.mData.size());
        for (PatriciaTrie.PtNode node : ptNodeArray.mData) {
            final int nodeSize = getPtNodeMaximumSize(node);
            node.mCachedSize = nodeSize;
            size += nodeSize;
        }
        ptNodeArray.mCachedSize = size;
    }

    /**
     * Initializes the cached addresses of node arrays and their containing nodes from their size.
     *
     * @param flatNodes the list of node arrays.
     * @return the byte size of the entire stack.
     */
    private static int initializePtNodeArraysCachedAddresses(
            final ArrayList<PatriciaTrie.PtNodeArray> flatNodes) {
        int nodeArrayOffset = 0;
        for (final PatriciaTrie.PtNodeArray nodeArray : flatNodes) {
            nodeArray.mCachedAddressBeforeUpdate = nodeArrayOffset;
            int nodeCountSize = getPtNodeCountSize(nodeArray.mData.size());
            int nodeffset = 0;
            for (final PatriciaTrie.PtNode ptNode : nodeArray.mData) {
                ptNode.mCachedAddressBeforeUpdate = ptNode.mCachedAddressAfterUpdate =
                        nodeCountSize + nodeArrayOffset + nodeffset;
                nodeffset += ptNode.mCachedSize;
            }
            nodeArrayOffset += nodeArray.mCachedSize;
        }
        return nodeArrayOffset;
    }

    /**
     * Computes the actual node array size, based on the cached addresses of the children nodes.
     *
     * Each node array stores its tentative address. During dictionary address computing, these
     * are not final, but they can be used to compute the node array size (the node array size
     * depends on the address of the children because the number of bytes necessary to store an
     * address depends on its numeric value. The return value indicates whether the node array
     * contents (as in, any of the addresses stored in the cache fields) have changed with
     * respect to their previous value.
     *
     * @param ptNodeArray the node array to compute the size of.
     * @return false if none of the cached addresses inside the node array changed, true otherwise.
     */
    private static boolean computeActualPtNodeArraySize(final PatriciaTrie.PtNodeArray ptNodeArray) {
        boolean changed = false;
        int size = getPtNodeCountSize(ptNodeArray.mData.size());
        for (PatriciaTrie.PtNode ptNode : ptNodeArray.mData) {
            ptNode.mCachedAddressAfterUpdate = ptNodeArray.mCachedAddressAfterUpdate + size;
            if (ptNode.mCachedAddressAfterUpdate != ptNode.mCachedAddressBeforeUpdate) {
                changed = true;
            }
            int nodeSize = getNodeHeaderSize(ptNode);
            if (null != ptNode.mChildren) {
                nodeSize += getByteSize(getOffsetToTargetNodeArrayDuringUpdate(ptNodeArray,
                        nodeSize + size, ptNode.mChildren));
            }
            ptNode.mCachedSize = nodeSize;
            size += nodeSize;
        }
        if (ptNodeArray.mCachedSize != size) {
            ptNodeArray.mCachedSize = size;
            changed = true;
        }
        return changed;
    }

    /**
     * Compute the binary size of the node count
     * @param count the node count
     * @return the size of the node count, either 1 or 2 bytes.
     */
    public static int getPtNodeCountSize(final int count) {
        if (MAX_PTNODES_FOR_ONE_BYTE_PTNODE_COUNT >= count) {
            return 1;
        } else if (MAX_PTNODES_IN_A_PT_NODE_ARRAY >= count) {
            System.out.println("XXX, node count size " + count + " is more than one byte");
            return 2;
        } else {
            throw new RuntimeException("Can't have more than "
                    + MAX_PTNODES_IN_A_PT_NODE_ARRAY + " PtNode in a PtNodeArray (found "
                    + count + ")");
        }
    }

    /**
     * Compute the maximum size of a PtNode, assuming 3-byte addresses for everything.
     *
     * @param ptNode the PtNode to compute the size of.
     * @return the maximum size of the PtNode.
     */
    private static int getPtNodeMaximumSize(final PatriciaTrie.PtNode ptNode) {
        int size = getNodeHeaderSize(ptNode);
        size += PTNODE_MAX_ADDRESS_SIZE; // For children address
        return size;
    }

    /**
     * Compute the size of the header (flag + characters size) of a PtNode.
     *
     * @param ptNode the PtNode of which to compute the size of the header
     */
    private static int getNodeHeaderSize(final PatriciaTrie.PtNode ptNode) {
        if (ptNode.mChars.length == 1) return PTNODE_FLAGS_SIZE + 2;
        return PTNODE_FLAGS_SIZE + 2*ptNode.mChars.length + PTNODE_TERMINATOR_SIZE;
    }

    /**
     * Get the offset from a position inside a current node array to a target node array, during
     * update.
     *
     * If the current node array is before the target node array, the target node array has not
     * been updated yet, so we should return the offset from the old position of the current node
     * array to the old position of the target node array. If on the other hand the target is
     * before the current node array, it already has been updated, so we should return the offset
     * from the new position in the current node array to the new position in the target node
     * array.
     *
     * @param currentNodeArray node array containing the PtNode where the offset will be written
     * @param offsetFromStartOfCurrentNodeArray offset, in bytes, from the start of currentNodeArray
     * @param targetNodeArray the target node array to get the offset to
     * @return the offset to the target node array
     */
    private static int getOffsetToTargetNodeArrayDuringUpdate(
            final PatriciaTrie.PtNodeArray currentNodeArray,
            final int offsetFromStartOfCurrentNodeArray,
            final PatriciaTrie.PtNodeArray targetNodeArray) {
        final boolean isTargetBeforeCurrent = (targetNodeArray.mCachedAddressBeforeUpdate
                < currentNodeArray.mCachedAddressBeforeUpdate);
        if (isTargetBeforeCurrent) {
            return targetNodeArray.mCachedAddressAfterUpdate
                    - (currentNodeArray.mCachedAddressAfterUpdate
                    + offsetFromStartOfCurrentNodeArray);
        }
        return targetNodeArray.mCachedAddressBeforeUpdate
                - (currentNodeArray.mCachedAddressBeforeUpdate + offsetFromStartOfCurrentNodeArray);
    }

    /**
     * Updates the cached addresses of node arrays after recomputing their new positions.
     *
     * @param flatNodes the list of node arrays.
     */
    private static void updatePtNodeArraysCachedAddresses(final ArrayList<PatriciaTrie.PtNodeArray> flatNodes) {
        for (final PatriciaTrie.PtNodeArray nodeArray : flatNodes) {
            nodeArray.mCachedAddressBeforeUpdate = nodeArray.mCachedAddressAfterUpdate;
            for (final PatriciaTrie.PtNode ptNode : nodeArray.mData) {
                ptNode.mCachedAddressBeforeUpdate = ptNode.mCachedAddressAfterUpdate;
            }
        }
    }

    /**
     * Compute the size, in bytes, that an address will occupy.
     *
     * This can be used either for children addresses (which are always positive) or for
     * attribute, which may be positive or negative but
     * store their sign bit separately.
     *
     * @param address the address
     * @return the byte size.
     */
    public static int getByteSize(final int address) {
        assert(address <= UINT24_MAX);
        if (!hasChildrenAddress(address)) {
            return 0;
        } else if (Math.abs(address) <= UINT8_MAX) {
            return 1;
        } else if (Math.abs(address) <= UINT16_MAX) {
            return 2;
        } else {
            return 3;
        }
    }

    /**
     * Helper method to hide the actual value of the no children address.
     */
    public static boolean hasChildrenAddress(final int address) {
        return NO_CHILDREN_ADDRESS != address;
    }

    /**
     * Sanity-checking method.
     *
     * This method checks a list of PtNode arrays for juxtaposition, that is, it will do
     * nothing if each node array's cached address is actually the previous node array's address
     * plus the previous node's size.
     * If this is not the case, it will throw an exception.
     *
     * @param arrays the list of node arrays to check
     */
    public static void checkFlatPtNodeArrayList(final ArrayList<PatriciaTrie.PtNodeArray> arrays) {
        int offset = 0;
        int index = 0;
        for (final PatriciaTrie.PtNodeArray ptNodeArray : arrays) {
            // BeforeUpdate and AfterUpdate addresses are the same here, so it does not matter
            // which we use.
            if (ptNodeArray.mCachedAddressAfterUpdate != offset) {
                throw new RuntimeException("Wrong address for node " + index
                        + " : expected " + offset + ", got " +
                        ptNodeArray.mCachedAddressAfterUpdate);
            }
            ++index;
            offset += ptNodeArray.mCachedSize;
        }
    }

    /**
     * Write a PtNodeArray. The PtNodeArray is expected to have its final position cached.
     *
     * @param ptNodeArray the node array to write.
     */
    public static int writePlacedPtNodeArray(byte[] buffer, final PatriciaTrie.PtNodeArray ptNodeArray) {
        int position = ptNodeArray.mCachedAddressAfterUpdate;

        final int ptNodeCount = ptNodeArray.mData.size();
        position = writePtNodeCount(buffer, position, ptNodeCount);
        for (int i = 0; i < ptNodeCount; ++i) {
            final PatriciaTrie.PtNode ptNode = ptNodeArray.mData.get(i);
            if (position != ptNode.mCachedAddressAfterUpdate) {
                throw new RuntimeException("Bug: write index is not the same as the cached address "
                        + "of the node : " + position + " <> "
                        + ptNode.mCachedAddressAfterUpdate);
            }
            position = writePtNode(buffer, position, ptNode);
        }
        if (position != ptNodeArray.mCachedAddressAfterUpdate + ptNodeArray.mCachedSize) {
            throw new RuntimeException("Not the same size : written "
                    + (position - ptNodeArray.mCachedAddressAfterUpdate)
                    + " bytes from a node that should have " + ptNodeArray.mCachedSize + " bytes");
        }
        return position;
    }

    public static int writePtNodeCount(byte[] buffer, int position, final int ptNodeCount) {
        final int countSize = getPtNodeCountSize(ptNodeCount);
        if (countSize != 1 && countSize != 2) {
            throw new RuntimeException("Strange size from getGroupCountSize : " + countSize);
        }
        final int encodedPtNodeCount = (countSize == 2) ?
                (ptNodeCount | LARGE_PTNODE_ARRAY_SIZE_FIELD_SIZE_FLAG) : ptNodeCount;

        return writeUIntToBuffer(buffer, position, encodedPtNodeCount, countSize);
    }

    public static int writeUIntToBuffer(final byte[] buffer, final int fromPosition, final int value,
                                 final int size) {
        int position = fromPosition;
        switch(size) {
            case 4:
                buffer[position++] = (byte) ((value >> 24) & 0xFF);
                /* fall through */
            case 3:
                buffer[position++] = (byte) ((value >> 16) & 0xFF);
                /* fall through */
            case 2:
                buffer[position++] = (byte) ((value >> 8) & 0xFF);
                /* fall through */
            case 1:
                buffer[position++] = (byte) (value & 0xFF);
                break;
            default:
                /* nop */
        }
        return position;
    }

    public static int writePtNode(byte[] buffer, int position, final PatriciaTrie.PtNode ptNode) {
        final int childrenPos = getChildrenPosition(ptNode);
        int pos = writeUIntToBuffer(buffer, position,
                makePtNodeFlags(ptNode.mChars.length > 1, ptNode.terminal, getByteSize(childrenPos)),
                PTNODE_FLAGS_SIZE);

        pos = writeCharacters(buffer, pos, ptNode.mChars, ptNode.hasSeveralChars());
        pos = writeChildrenPosition(buffer, pos, ptNode);
        return pos;
    }

    static final class CharEncoding {
        static int writeCharArray(char[] chars, byte[] buffer, int fromPosition) {
            int index = fromPosition;
            for (char c : chars) {
                buffer[index++] = (byte)(0xFF & ((int)c >> 8));
                buffer[index++] = (byte)(0xFF & (int)c);
            }
            return index;
        }
    }

    private static int writeCharacters(byte[] buffer, int position, final char[] chars, final boolean hasSeveralChars) {
        int pos = CharEncoding.writeCharArray(chars, buffer, position);
        if (hasSeveralChars) {
            buffer[pos++] = PTNODE_CHARACTERS_TERMINATOR;
        }
        return pos;
    }

    /**
     * Helper method to write a children position to a file.
     *
     * @param buffer the buffer to write to.
     * @param fromIndex the index in the buffer to write the address to.
     */
    private static int writeChildrenPosition(byte[] buffer, int fromIndex, final PatriciaTrie.PtNode ptNode) {
        final int childrenPos = getChildrenPosition(ptNode);
        int index = fromIndex;
        switch (getByteSize(childrenPos)) {
            case 1:
                buffer[index++] = (byte)childrenPos;
                break;
            case 2:
                buffer[index++] = (byte)(0xFF & (childrenPos >> 8));
                buffer[index++] = (byte)(0xFF & childrenPos);
                break;
            case 3:
                buffer[index++] = (byte)(0xFF & (childrenPos >> 16));
                buffer[index++] = (byte)(0xFF & (childrenPos >> 8));
                buffer[index++] = (byte)(0xFF & childrenPos);
                break;
            case 0:
                break;
            default:
                throw new RuntimeException("Position " + childrenPos+ " has a strange size");
        }
        return index;
    }

    public static int getChildrenPosition(final PatriciaTrie.PtNode ptNode) {
        int positionOfChildrenPosField = ptNode.mCachedAddressAfterUpdate + getNodeHeaderSize(ptNode);
        return null == ptNode.mChildren ? NO_CHILDREN_ADDRESS :
                ptNode.mChildren.mCachedAddressAfterUpdate - positionOfChildrenPosField;
    }

    /**
     * Makes the flag value for a PtNode.
     *
     * @param hasMultipleChars whether the PtNode has multiple chars.
     * @param isTerminal whether the PtNode is terminal.
     * @param childrenAddressSize the size of a children address.
     * @return the flags
     */
    static int makePtNodeFlags(final boolean hasMultipleChars, final boolean isTerminal, final int childrenAddressSize) {
        byte flags = 0;
        if (hasMultipleChars) flags |= FLAG_HAS_MULTIPLE_CHARS;
        if (isTerminal) flags |= FLAG_IS_TERMINAL;
        switch (childrenAddressSize) {
            case 1:
                flags |= FLAG_CHILDREN_ADDRESS_TYPE_ONEBYTE;
                break;
            case 2:
                flags |= FLAG_CHILDREN_ADDRESS_TYPE_TWOBYTES;
                break;
            case 3:
                flags |= FLAG_CHILDREN_ADDRESS_TYPE_THREEBYTES;
                break;
            case 0:
                flags |= FLAG_CHILDREN_ADDRESS_TYPE_NOADDRESS;
                break;
            default:
                throw new RuntimeException("Node with a strange address");
        }
        return flags;
    }

    /**
     * Dumps a collection of useful statistics about a list of PtNode arrays.
     *
     * This prints purely informative stuff, like the total estimated file size, the
     * number of PtNode arrays, of PtNodes, the repartition of each address size, etc
     *
     * @param ptNodeArrays the list of PtNode arrays.
     */
    /* package */ static String showStatistics(ArrayList<PatriciaTrie.PtNodeArray> ptNodeArrays) {
        int firstTerminalAddress = Integer.MAX_VALUE;
        int lastTerminalAddress = Integer.MIN_VALUE;
        int size = 0;
        int ptNodes = 0;
        int maxNodes = 0;
        int maxRuns = 0;
        for (final PatriciaTrie.PtNodeArray ptNodeArray : ptNodeArrays) {
            if (maxNodes < ptNodeArray.mData.size()) maxNodes = ptNodeArray.mData.size();
            for (final PatriciaTrie.PtNode ptNode : ptNodeArray.mData) {
                ++ptNodes;
                if (ptNode.mChars.length > maxRuns) maxRuns = ptNode.mChars.length;
                if (ptNode.isTerminal()) {
                    if (ptNodeArray.mCachedAddressAfterUpdate < firstTerminalAddress)
                        firstTerminalAddress = ptNodeArray.mCachedAddressAfterUpdate;
                    if (ptNodeArray.mCachedAddressAfterUpdate > lastTerminalAddress)
                        lastTerminalAddress = ptNodeArray.mCachedAddressAfterUpdate;
                }
            }
            if (ptNodeArray.mCachedAddressAfterUpdate + ptNodeArray.mCachedSize > size) {
                size = ptNodeArray.mCachedAddressAfterUpdate + ptNodeArray.mCachedSize;
            }
        }
        final int[] ptNodeCounts = new int[maxNodes + 1];
        final int[] runCounts = new int[maxRuns + 1];
        for (final PatriciaTrie.PtNodeArray ptNodeArray : ptNodeArrays) {
            ++ptNodeCounts[ptNodeArray.mData.size()];
            for (final PatriciaTrie.PtNode ptNode : ptNodeArray.mData) {
                ++runCounts[ptNode.mChars.length];
            }
        }

        return "Statistics:\n"
                + "  Total file size " + size + "\n"
                + "  " + ptNodeArrays.size() + " node arrays\n"
                + "  " + ptNodes + " PtNodes (" + ((float)ptNodes / ptNodeArrays.size())
                + " PtNodes per node)\n"
                + "  First terminal at " + firstTerminalAddress + "\n"
                + "  Last terminal at " + lastTerminalAddress + "\n"
                + "  PtNode stats : max = " + maxNodes;
    }
}

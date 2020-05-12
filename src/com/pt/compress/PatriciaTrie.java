package com.pt.compress;

import com.pt.AbstractPatriciaTrie;

import java.util.*;

public class PatriciaTrie implements AbstractPatriciaTrie {
    private static int CHARACTER_NOT_FOUND_INDEX = -1;
    private static int ARRAYS_ARE_EQUAL = 0;

    /**
     * A node array of the dictionary, containing several PtNodes.
     *
     * A PtNodeArray is but an ordered array of PtNodes, which essentially contain all the
     * real information.
     * This class also contains fields to cache size and address, to help with binary
     * generation.
     */
    public static final class PtNodeArray {
        public ArrayList<PtNode> mData;
        // To help with binary generation
        int mCachedSize = Integer.MIN_VALUE;
        // mCachedAddressBefore/AfterUpdate are helpers for binary dictionary generation. They
        // always hold the same value except between dictionary address compression, during which
        // the update process needs to know about both values at the same time. Updating will
        // update the AfterUpdate value, and the code will move them to BeforeUpdate before
        // the next update pass.
        int mCachedAddressBeforeUpdate = Integer.MIN_VALUE;
        int mCachedAddressAfterUpdate = Integer.MIN_VALUE;

        public PtNodeArray() {
            mData = new ArrayList<>();
        }

        public PtNodeArray(ArrayList<PtNode> data) {
            Collections.sort(data, PTNODE_COMPARATOR);
            mData = data;
        }
    }

    /**
     * PtNode is a group of characters, with probability information, shortcut targets, bigrams,
     * and children (Pt means Patricia Trie).
     *
     * This is the central class of the in-memory representation. A PtNode is what can
     * be seen as a traditional "trie node", except it can hold several characters at the
     * same time. A PtNode essentially represents one or several characters in the middle
     * of the trie tree; as such, it can be a terminal, and it can have children.
     * In this in-memory representation, whether the PtNode is a terminal or not is represented
     * by mProbabilityInfo. The PtNode is a terminal when the mProbabilityInfo is not null and the
     * PtNode is not a terminal when the mProbabilityInfo is null. A terminal may have non-null
     * shortcuts and/or bigrams, but a non-terminal may not. Moreover, children, if present,
     * are non-null.
     */
    public static final class PtNode {
        private static final int NOT_A_TERMINAL = -1;
        public char mChars[] = new char[0];
        public boolean terminal = false;
        PtNodeArray mChildren;

        public PtNode() {
            mChars = new char[0];
        }

        // mCachedSize and mCachedAddressBefore/AfterUpdate are helpers for binary dictionary
        // generation. Before and After always hold the same value except during dictionary
        // address compression, where the update process needs to know about both values at the
        // same time. Updating will update the AfterUpdate value, and the code will move them
        // to BeforeUpdate before the next update pass.
        // The update process does not need two versions of mCachedSize.
        int mCachedSize; // The size, in bytes, of this PtNode.
        int mCachedAddressBeforeUpdate; // The address of this PtNode (before update)
        int mCachedAddressAfterUpdate; // The address of this PtNode (after update)

        public PtNode(char [] chars) {
            mChars = chars;
        }

        public void addChild(PtNode n) {
            if (null == mChildren) {
                mChildren = new PtNodeArray();
            }
            mChildren.mData.add(n);
        }

        public boolean isTerminal() {
            return terminal;
        }

        public PtNodeArray getChildren() {
            return mChildren;
        }

        public boolean hasSeveralChars() {
            assert(mChars.length > 0);
            return 1 < mChars.length;
        }
    }

    /**
     * Helper class that compares and sorts two PtNodes according to their
     * first element only. I repeat: ONLY the first element is considered, the rest
     * is ignored.
     * This comparator imposes orderings that are inconsistent with equals.
     */
    static final class PTNodeComparator implements Comparator<PtNode> {
        @Override
        public int compare(PtNode p1, PtNode p2) {
            if (p1.mChars[0] == p2.mChars[0]) return 0;
            return p1.mChars[0] < p2.mChars[0] ? -1 : 1;
        }
    }
    final static PTNodeComparator PTNODE_COMPARATOR = new PTNodeComparator();

    private int mWordsCount = 0;
    private PtNode mRoot = new PtNode();

    public PatriciaTrie() {
        mRoot.mChildren = new PtNodeArray();
    }

    public int getWordsCount() {
        return mWordsCount;
    }

    public PtNode getRoot() {
        return mRoot;
    }

    @Override
    public Iterator<String> iterator() {
        return new PatriciaTrieIterator(mRoot);
    }

    public void addWord(String word) {
        mWordsCount++;
        char[] chars = word.toCharArray();
        PtNode current = mRoot;
        int charIndex = 0;

        int differentCharIndex = 0; // Set by the loop to the index of the char that differs
        int nodeIndex = findIndexOfChar(current.mChildren, chars[charIndex]);
        // traverse until not match
        while (CHARACTER_NOT_FOUND_INDEX != nodeIndex) {
            current = current.mChildren.mData.get(nodeIndex);
            differentCharIndex = compareCharArrays(current.mChars, chars, charIndex);
            if (ARRAYS_ARE_EQUAL != differentCharIndex && differentCharIndex < current.mChars.length)
                break;
            // differentIndex == 0 (all equal) || differentIndex >= current.char.length
            if (current.mChildren == null || current.mChildren.mData.isEmpty()) break;
            charIndex += current.mChars.length;
            if (charIndex >= chars.length) break;
            nodeIndex = findIndexOfChar(current.mChildren, chars[charIndex]);
        }

        if (CHARACTER_NOT_FOUND_INDEX == nodeIndex) {
            // No node at this point to accept the word. Create one.
            final int insertionIndex = findInsertionIndex(current.mChildren, chars[charIndex]);
            final PtNode node = new PtNode(Arrays.copyOfRange(chars, charIndex, chars.length));
            node.terminal = true;
            current.mChildren.mData.add(insertionIndex, node);
            checkStack(current.mChildren);
        } else {
            // There is a word with a common prefix.
            if (differentCharIndex == current.mChars.length) {
                if (charIndex + differentCharIndex >= chars.length) {
                    // The new word is a prefix of an existing word, but the node on which it
                    // should end already exists as is. Since the old PtNode was not a terminal,
                    // make it one by filling in its frequency and other attributes
                    current.terminal = true;
                } else {
                    // current nodes children must be empty
                    // The new word matches the full old word and extends past it.
                    // We only have to create a new node and add it to the end of this.
                    final PtNode node = new PtNode(Arrays.copyOfRange(chars, charIndex+differentCharIndex, chars.length));
                    node.terminal = true;
                    current.mChildren = new PtNodeArray();
                    current.mChildren.mData.add(node);
                }
            } else {
                if (differentCharIndex == 0) {
                    // Exact same word. Update the frequency if higher. This will also add the
                    // new shortcuts to the existing shortcut list if it already exists.
                    current.terminal = true;
                } else {
                    // Partial prefix match only. We have to replace the current node with a node
                    // containing the current prefix and create two new ones for the tails.
                    PtNode splittedNode = new PtNode(Arrays.copyOfRange(current.mChars, differentCharIndex, current.mChars.length));
                    splittedNode.terminal = current.terminal;
                    splittedNode.mChildren = current.mChildren;

                    current.mChars = Arrays.copyOfRange(current.mChars, 0, differentCharIndex);
                    current.terminal = false;
                    current.mChildren = new PtNodeArray();
                    current.mChildren.mData.add(splittedNode);
                    if (charIndex + differentCharIndex >= chars.length) {
                        current.terminal = true;
                    } else {
                        final PtNode newWord = new PtNode(Arrays.copyOfRange(chars,
                                charIndex+differentCharIndex, chars.length));
                        newWord.terminal = true;
                        final int addIndex = chars[charIndex+differentCharIndex] > splittedNode.mChars[0] ? 1 : 0;
                        current.mChildren.mData.add(addIndex, newWord);
                    }
                }
                checkStack(current.mChildren);
            }
        }
    }

    /**
     * Recursively count the number of PtNodes in a given branch of the trie.
     *
     * @param nodeArray the parent node.
     * @return the number of PtNodes in all the branch under this node.
     */
    public static int countPtNodes(final PtNodeArray nodeArray) {
        final int nodeSize = nodeArray.mData.size();
        int size = nodeSize;
        for (int i = nodeSize - 1; i >= 0; --i) {
            PtNode ptNode = nodeArray.mData.get(i);
            if (null != ptNode.mChildren)
                size += countPtNodes(ptNode.mChildren);
        }
        return size;
    }

    /**
     * Find the index of a char in a node array, if it exists.
     *
     * @param node the node array to search in.
     * @param character the character to search for.
     * @return the position of the character if it's there, or CHARACTER_NOT_FOUND_INDEX = -1 else.
     */
    private static int findIndexOfChar(final PtNodeArray node, char character) {
        final int insertionIndex = findInsertionIndex(node, character);
        if (node.mData.size() <= insertionIndex) return CHARACTER_NOT_FOUND_INDEX;
        return character == node.mData.get(insertionIndex).mChars[0] ? insertionIndex
                : CHARACTER_NOT_FOUND_INDEX;
    }

    /**
     * Finds the insertion index of a character within a node array.
     */
    private static int findInsertionIndex(final PtNodeArray node, char character) {
        final ArrayList<PtNode> data = node.mData;
        final PtNode reference = new PtNode(new char[] { character });
        int result = Collections.binarySearch(data, reference, PTNODE_COMPARATOR);
        return result >= 0 ? result : -result - 1;
    }

    /**
     * Custom comparison of two int arrays taken to contain character codes.
     *
     * This method compares the two arrays passed as an argument in a lexicographic way,
     * with an offset in the dst string.
     * This method does NOT test for the first character. It is taken to be equal.
     * I repeat: this method starts the comparison at 1 <> dstOffset + 1.
     * The index where the strings differ is returned. ARRAYS_ARE_EQUAL = 0 is returned if the
     * strings are equal. This works BECAUSE we don't look at the first character.
     *
     * @param src the left-hand side string of the comparison.
     * @param dst the right-hand side string of the comparison.
     * @param dstOffset the offset in the right-hand side string.
     * @return the index at which the strings differ, or ARRAYS_ARE_EQUAL = 0 if they don't.
     */
    private static int compareCharArrays(final char[] src, final char[] dst, int dstOffset) {
        // We do NOT test the first char, because we come from a method that already
        // tested it.
        for (int i = 1; i < src.length; ++i) {
            if (dstOffset + i >= dst.length) return i;
            if (src[i] != dst[dstOffset + i]) return i;
        }
        if (dst.length > src.length) return src.length;
        return ARRAYS_ARE_EQUAL;
    }


    /**
     * Sanity check for a PtNode array.
     *
     * This method checks that all PtNodes in a node array are ordered as expected.
     * If they are, nothing happens. If they aren't, an exception is thrown.
     */
    private static void checkStack(PtNodeArray node) {
        ArrayList<PtNode> stack = node.mData;
        int lastValue = -1;
        for (int i = 0; i < stack.size(); ++i) {
            int currentValue = stack.get(i).mChars[0];
            if (currentValue <= lastValue) {
                throw new RuntimeException("Invalid stack");
            }
            lastValue = currentValue;
        }
    }
}

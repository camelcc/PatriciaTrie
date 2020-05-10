import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class SimplePatriciaTrie {
    private static int CHARACTER_NOT_FOUND_INDEX = -1;
    private static int ARRAYS_ARE_EQUAL = 0;

    public static class PatriciaTrieNode {
        char[] mChars = new char[0];
        boolean terminal = false;
        ArrayList<PatriciaTrieNode> children = new ArrayList<>();

        public PatriciaTrieNode() {}

        public PatriciaTrieNode(char[] chars) {
            mChars = chars;
        }
    }

    /**
     * Helper class that compares and sorts two PtNodes according to their
     * first element only. I repeat: ONLY the first element is considered, the rest
     * is ignored.
     * This comparator imposes orderings that are inconsistent with equals.
     */
    static final class PTNodeComparator implements Comparator<PatriciaTrieNode> {
        @Override
        public int compare(PatriciaTrieNode p1, PatriciaTrieNode p2) {
            if (p1.mChars[0] == p2.mChars[0]) return 0;
            return p1.mChars[0] < p2.mChars[0] ? -1 : 1;
        }
    }
    final static PTNodeComparator PTNODE_COMPARATOR = new PTNodeComparator();

    private int mWordsCount = 0;
    private PatriciaTrieNode mRoot = new PatriciaTrieNode();

    public int getWordsCount() {
        return mWordsCount;
    }

    public PatriciaTrieNode getRoot() {
        return mRoot;
    }

    public void addWord(String word) {
        System.out.println("add word: " + word);
        mWordsCount++;
        char[] chars = word.toCharArray();

        PatriciaTrieNode current = mRoot;
        int charIndex = 0;
        int differentCharIndex = 0; // Set by the loop to the index of the char that differs
        int nodeIndex = findIndexOfChar(current, chars[charIndex]);
        // traverse until not match
        while (CHARACTER_NOT_FOUND_INDEX != nodeIndex) {
            current = current.children.get(nodeIndex);
            differentCharIndex = compareCharArrays(current.mChars, chars, charIndex);
            if (ARRAYS_ARE_EQUAL != differentCharIndex && differentCharIndex < current.mChars.length)
                break;
            // differentIndex == 0 (all equal) || differentIndex >= current.char.length
            if (current.children.isEmpty()) break;
            charIndex += current.mChars.length;
            if (charIndex >= chars.length) break;
            nodeIndex = findIndexOfChar(current, chars[charIndex]);
        }

        if (CHARACTER_NOT_FOUND_INDEX == nodeIndex) {
            // No node at this point to accept the word. Create one.
            final int insertionIndex = findInsertionIndex(current, chars[charIndex]);
            final PatriciaTrieNode node = new PatriciaTrieNode(Arrays.copyOfRange(chars, charIndex, chars.length));
            node.terminal = true;
            current.children.add(insertionIndex, node);
            checkStack(current);
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
                    final PatriciaTrieNode node = new PatriciaTrieNode(Arrays.copyOfRange(chars, charIndex+differentCharIndex, chars.length));
                    node.terminal = true;
                    current.children = new ArrayList<>();
                    current.children.add(node);
                }
            } else {
                if (differentCharIndex == 0) {
                    // Exact same word. Update the frequency if higher. This will also add the
                    // new shortcuts to the existing shortcut list if it already exists.
                    current.terminal = true;
                } else {
                    // Partial prefix match only. We have to replace the current node with a node
                    // containing the current prefix and create two new ones for the tails.
                    PatriciaTrieNode splittedNode = new PatriciaTrieNode(Arrays.copyOfRange(current.mChars, differentCharIndex, current.mChars.length));
                    splittedNode.terminal = current.terminal;
                    splittedNode.children = current.children;

                    current.mChars = Arrays.copyOfRange(current.mChars, 0, differentCharIndex);
                    current.terminal = false;
                    current.children = new ArrayList<>();
                    current.children.add(splittedNode);
                    if (charIndex + differentCharIndex >= chars.length) {
                        current.terminal = true;
                    } else {
                        final PatriciaTrieNode newWord = new PatriciaTrieNode(Arrays.copyOfRange(chars,
                                charIndex+differentCharIndex, chars.length));
                        newWord.terminal = true;
                        final int addIndex = chars[charIndex+differentCharIndex] > splittedNode.mChars[0] ? 1 : 0;
                        current.children.add(addIndex, newWord);
                    }
                }
                checkStack(current);
            }
        }
    }

    /**
     * Find the index of a char in a node array, if it exists.
     *
     * @param node the node array to search in.
     * @param character the character to search for.
     * @return the position of the character if it's there, or CHARACTER_NOT_FOUND_INDEX = -1 else.
     */
    private static int findIndexOfChar(final PatriciaTrieNode node, char character) {
        final int insertionIndex = findInsertionIndex(node, character);
        if (node.children.size() <= insertionIndex) return CHARACTER_NOT_FOUND_INDEX;
        return character == node.children.get(insertionIndex).mChars[0] ? insertionIndex
                : CHARACTER_NOT_FOUND_INDEX;
    }

    private static int findInsertionIndex(final PatriciaTrieNode node, char character) {
        final PatriciaTrieNode reference = new PatriciaTrieNode(new char[]{character});
        int result = Collections.binarySearch(node.children, reference, PTNODE_COMPARATOR);
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
    private static void checkStack(PatriciaTrieNode node) {
        ArrayList<PatriciaTrieNode> stack = node.children;
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

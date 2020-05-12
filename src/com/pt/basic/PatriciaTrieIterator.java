package com.pt.basic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

public class PatriciaTrieIterator implements Iterator<String> {
    private static final class Position {
        public Iterator<PatriciaTrie.PtNode> pos;
        public int length;
        public Position(ArrayList<PatriciaTrie.PtNode> ptNodes) {
            pos = ptNodes.iterator();
            length = 0;
        }
    }
    final StringBuilder mCurrentString;
    final LinkedList<Position> mPositions;

    public PatriciaTrieIterator(PatriciaTrie.PtNode root) {
        mCurrentString = new StringBuilder();
        mPositions = new LinkedList<>();
        final Position rootPos = new Position(root.children);
        mPositions.add(rootPos);
    }

    @Override
    public boolean hasNext() {
        for (Position p : mPositions) {
            if (p.pos.hasNext()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String next() {
        Position currentPos = mPositions.getLast();
        mCurrentString.setLength(currentPos.length);

        do {
            if (currentPos.pos.hasNext()) {
                final PatriciaTrie.PtNode currentPtNode = currentPos.pos.next();
                currentPos.length = mCurrentString.length();
                mCurrentString.append(currentPtNode.mChars);
                if (null != currentPtNode.children) {
                    currentPos = new Position(currentPtNode.children);
                    currentPos.length = mCurrentString.length();
                    mPositions.addLast(currentPos);
                }
                if (currentPtNode.terminal) {
                    return mCurrentString.toString();
                }
            } else {
                mPositions.removeLast();
                currentPos = mPositions.getLast();
                mCurrentString.setLength(mPositions.getLast().length);
            }
        } while (true);
    }
}

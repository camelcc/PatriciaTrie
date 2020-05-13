package com.pt.compress;

import com.pt.AbstractPatriciaTrie;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BinaryPatriciaTrie implements AbstractPatriciaTrie {
    private byte[] data;
    private int rootIndex;

    public BinaryPatriciaTrie(File dictionary) throws IOException {
        data = Files.readAllBytes(dictionary.toPath());
        rootIndex = DecoderUtils.parseHeader(data);
    }

    @Override
    public void addWord(String word) {}

    @Override
    public boolean contains(String word) {
        StringBuilder sb = new StringBuilder();
        int[] nodeIndex = DecoderUtils.contains(sb, data, rootIndex, word.toCharArray(), 0);
        if (nodeIndex[0] == -1) {
            return false;
        }
        if (nodeIndex[1] == 0) {
            return false;
        }
        return sb.toString().equals(word);
    }

    @Override
    public List<String> search(String prefix) {
        StringBuilder sb = new StringBuilder();
        int[] nodeIndex = DecoderUtils.contains(sb, data, rootIndex, prefix.toCharArray(), 0);
        if (nodeIndex[0] == -1) {
            return new ArrayList<>();
        }
        ArrayList<String> res = new ArrayList<>();
        if (nodeIndex[1] == 1) {
            res.add(sb.toString());
        }
        if (nodeIndex[0] == 0) {
            return res;
        }
        DecoderUtils.traverse(data, nodeIndex[0], sb, res);
        return res;
    }

    @Override
    public Iterator<String> iterator() {
        return null;
    }
}

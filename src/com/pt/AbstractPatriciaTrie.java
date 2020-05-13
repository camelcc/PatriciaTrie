package com.pt;

import java.util.List;

public interface AbstractPatriciaTrie extends Iterable<String> {
    void addWord(String word);
    boolean contains(String word);
    List<String> search(String prefix);
}

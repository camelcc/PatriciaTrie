# [Patricia Trie](https://en.wikipedia.org/wiki/Radix_tree)

This repo is for practicing **patricia trie** purpose. Implementation is referring the AOSP source code.
[AOSP FusionDictionary](https://cs.android.com/android/platform/superproject/+/master:packages/inputmethods/LatinIME/tests/src/com/android/inputmethod/latin/makedict/FusionDictionary.java)

The goal of this repo is using JAVA rather than JNI/C++ to implement the same capability of making a binary dictionary and use it the same way as AOSP.
Internally AOSP using the `patricia trie` to accomplish type ahead suggestion and words correction.


The repo have several packages as I am trying to strip and re-add the functionality into the implementation.  
The performance test is performed on my personal Android (OnePlus 7T, Android 10) phone.   
Operation: 
1. Read the `en_wordlist.combined` into memory.
2. Compress it and save it into a binary file.
3. Load the binary into memory.
4. Perform words search.

## pt.basic  

The pt.basic package contains the minimum implementation support only `add` operation.

- Testing only #1   
  ~1s and 17M to load into memory.
  
## pt.compress

Introduce the compressing steps preparing for binary format. The dictionary compressing part is refer the AOSP v2 encoder.

- Testing #2
  The output file is around 1.3MB.







package com.pt

class DictionaryParser(private val pt: AbstractPatriciaTrie) {
    private val validator = PTValidator(pt)

    fun feed(line: String) {
        if (line.startsWith("dictionary")) {
            println("start parsing dictionary: $line")
            return
        }

        val l = line.trim()
        // only care word
        if (!l.startsWith("word")) {
            return
        }
        val word = line.split(',')[0].split('=')[1]
        validator.addWord(word)
    }

    fun validate() {
        validator.validate()
    }
}
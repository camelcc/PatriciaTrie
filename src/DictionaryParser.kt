
class DictionaryParser(val pt: SimplePatriciaTrie) {
    private val validator = PTValidator()

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
        pt.addWord(word)
    }

    fun validate() {
        validator.validate(pt.root)
    }
}
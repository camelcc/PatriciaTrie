import com.pt.DictionaryParser
import com.pt.compress.PatriciaTrie
import java.io.*
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

fun main() {
    val dictionary = File("./en_wordlist.combined")
    if (!dictionary.exists()) {
        println("can not find dictionary file en_wordlist.combined")
        exitProcess(1)
    }
    val pt = PatriciaTrie()
    val parser = DictionaryParser(pt)
    val parseTime = measureTimeMillis {
        BufferedReader(InputStreamReader(FileInputStream(dictionary))).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                parser.feed(line)
                line = reader.readLine()
            }
        }
    }
    parser.validate()
    println("loading finished, took time $parseTime ms, words: ${pt.wordsCount}")
}

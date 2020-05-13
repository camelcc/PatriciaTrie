import com.pt.PTParser
import com.pt.compress.BinaryPatriciaTrie
import com.pt.compress.PatriciaTrie
import com.pt.compress.PatriciaTrieEncoder
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
    val parser = PTParser(pt)
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

    val dict = File("./wordlist.dict")
    if (dict.exists()) {
        dict.delete()
    }
    dict.createNewFile()
    var encodeTime = measureTimeMillis {
        val encoder = PatriciaTrieEncoder()
        encoder.writeDictionary(dict, pt)
    }
    println("dump finished, took time $encodeTime ms")

    val bpt = BinaryPatriciaTrie(dict)

    var key = readLine()
    while (key != null) {
        if (key.length <= 3) {
            println("skip suggestions")
            continue
        }
        print("suggestions for $key: ")
        for (v in bpt.search(key)) {
            print("$v ")
        }
        println()
        key = readLine()
    }
}

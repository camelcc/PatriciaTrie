import java.lang.RuntimeException
import java.lang.StringBuilder

class PTValidator {
    val pool = HashSet<String>()

    fun addWord(word: String) {
        pool.add(word)
    }

    fun validate(root: SimplePatriciaTrie.PatriciaTrieNode) {
        dfs(StringBuilder(), root)
        if (pool.isNotEmpty()) {
            throw RuntimeException("missing ${pool.size} words")
        }
    }

    private fun dfs(current: StringBuilder, node: SimplePatriciaTrie.PatriciaTrieNode) {
        val next = StringBuilder(current).append(node.mChars)
        if (node.terminal) {
            if (!pool.contains(next.toString())) {
                throw RuntimeException("$next don't exist")
            } else {
                pool.remove(next.toString())
            }
        }
        if (node.children.isEmpty() && !node.terminal) {
            throw RuntimeException("$next have no children, neither a valid word")
        }
        for (c in node.children) {
            dfs(next, c)
        }
    }
}
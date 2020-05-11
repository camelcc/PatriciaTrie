package com.pt

class PTValidator(private val pt: AbstractPatriciaTrie) {
    private val pool = HashSet<String>()

    fun addWord(word: String) {
        pool.add(word)
        pt.addWord(word)
    }

    fun validate() {
        for (word in pt) {
            if (!pool.contains(word)) {
                throw RuntimeException("$word don't exist")
            } else {
                pool.remove(word)
            }
        }
        if (pool.isNotEmpty()) {
            throw RuntimeException("missing ${pool.size} words")
        }
    }

//    private fun dfs(current: StringBuilder, node: PatriciaTrie.PtNode) {
//        val next = StringBuilder(current).append(node.mChars)
//        if (node.isTerminal) {
//            if (!pool.contains(next.toString())) {
//                throw RuntimeException("$next don't exist")
//            } else {
//                pool.remove(next.toString())
//            }
//        }
//        if ((node.children == null || node.children.mData.isNullOrEmpty()) && !node.isTerminal) {
//            throw RuntimeException("$next have no children, neither a valid word")
//        }
//        if (node.children == null) {
//            return
//        }
//        for (c in node.children.mData) {
//            dfs(next, c)
//        }
//    }
}
package com.pt.compress

import java.io.File
import java.io.FileOutputStream

class PatriciaTrieEncoder {
    fun writeDictionary(file: File, pt: PatriciaTrie) {
        val os = FileOutputStream(file)
        os.use { outputStream ->
            EncoderUtils.writeDictionaryHeader(outputStream)

            // Addresses are limited to 3 bytes, but since addresses can be relative to each node
            // array, the structure itself is not limited to 16MB. However, if it is over 16MB deciding
            // the order of the PtNode arrays becomes a quite complicated problem, because though the
            // dictionary itself does not have a size limit, each node array must still be within 16MB
            // of all its children and parents. As long as this is ensured, the dictionary file may
            // grow to any size.

            // Leave the choice of the optimal node order to the flattenTree function.
            println("Flattening the tree...")

            // this is a DFS traversal
            val flatNodes = EncoderUtils.flattenTree(pt)

            println("Computing addresses...")
            EncoderUtils.computeAddresses(flatNodes)
            println("Checking PtNode array...")
            EncoderUtils.checkFlatPtNodeArrayList(flatNodes)

            // Create a buffer that matches the final dictionary size.
            val lastNodeArray = flatNodes.get(flatNodes.size - 1)
            val bufferSize = lastNodeArray.mCachedAddressAfterUpdate + lastNodeArray.mCachedSize
            val buffer = ByteArray(bufferSize)

            println("Writing file...")

            var position = 0
            for (nodeArray in flatNodes) {
                position = EncoderUtils.writePlacedPtNodeArray(buffer, nodeArray);
            }
            println(EncoderUtils.showStatistics(flatNodes))

            outputStream.write(buffer, 0, position)
        }
    }
}
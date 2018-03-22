package org.github.sigerber.confluence.comment.generator

import java.util.*

typealias Prefix = List<String>
typealias Suffixes = MutableList<String>
typealias Chain = MutableMap<Prefix, Suffixes>

object MarkovGenerator {

    fun trainMarkovChain(sourceStrings: List<String>, gramSize: Int = 3): Chain {
        if (gramSize <= 2)
            throw IllegalArgumentException("Gram size must be greater than 2. Was $gramSize.")

        val tokens = sourceStrings.flatMap(MarkovGenerator::tokenize)
        val chain: Chain = mutableMapOf()
        val prefixSize = gramSize - 1

        if (tokens.size > gramSize) {
            var prefix = tokens.take(prefixSize)

            var offset = prefixSize
            do {
                val suffixes = chain.getOrPut(prefix, ::mutableListOf)

                val nextWord = tokens[offset++]
                suffixes.add(nextWord)

                prefix = prefix.takeLast(prefix.size - 1) + nextWord
            } while (offset < tokens.size)
        }

        return chain
    }

    fun generateParagraphs(chain: Chain, minParagraphs: Int = 1, maxParagraphs: Int = 3, minSentences: Int = 1, maxSentences: Int = 7, separator: String = "\n\n"): String {
        if (maxParagraphs < minParagraphs) throw IllegalArgumentException("Maximum number of paragraphs to generate ($maxParagraphs) should be less that the minimum number ($minParagraphs)")

        val paragaphs = (1..(Random().nextInt(maxParagraphs) + minParagraphs)).map { _ -> generateParagraph(chain, minSentences, maxSentences) }
        return paragaphs.joinToString(separator = separator)
    }

    fun generateParagraph(chain: Chain, minSentences: Int = 1, maxSentences: Int = 7): String {
        if (maxSentences < minSentences) throw IllegalArgumentException("Maximum number of sentences to generate ($maxSentences) should be less that the minimum number ($minSentences)")

        val sentences = (1..Random().nextInt(maxSentences) + minSentences).map { _ -> generateSentence(chain) }
        return sentences.joinToString(separator = " ")
    }

    fun generateSentence(chain: Chain, maxLength: Int = 25): String {
        var prefix = chooseStartingOfSentencePrefix(chain)
        var sentance = prefix.joinToString(" ")

        var currentLength = 2
        do {
            val nextWord = chooseNextWord(prefix, chain)
            if (nextWord != null) {
                sentance = "$sentance $nextWord"
                prefix = prefix.takeLast(prefix.size - 1) + nextWord
                currentLength++
            }
        } while (nextWord != null && !nextWord.isEndOfSentence() && currentLength <= maxLength)

        if (!sentance.isEndOfSentence()) {
            sentance += "."
        }

        return sentance
    }

    private fun tokenize(source: String): List<String> = source
            .replace(Regex("\\p{C}"), "") // Remove non-printing characters
            .split(Regex("\\s+")) // Split on white-space
            .map(String::trim) // Remove leading and trailing whitespace
            .filter(String::isNotBlank) // Remove any surviving whitespace-only tokens

    private fun chooseStartingOfSentencePrefix(chain: Chain): Prefix {
        val prefixesThatDoNotEndSentances = chain.filterKeys { !it[0].isEndOfSentence() }
        val largerSuffixes = prefixesThatDoNotEndSentances.filterValues { it.size > 1 }
        val capitalizedPrefixes = largerSuffixes.filterKeys { it[0][0].isUpperCase() }

        val startingSet = if (capitalizedPrefixes.isNotEmpty()) {
            capitalizedPrefixes
        } else if (largerSuffixes.isNotEmpty()) {
            largerSuffixes
        } else if (prefixesThatDoNotEndSentances.isEmpty()) {
            prefixesThatDoNotEndSentances
        } else {
            chain
        }

        return chooseRandomPrefix(startingSet.toMutableMap())
    }

    private fun chooseRandomPrefix(chain: Chain): Prefix {
        val values = chain.keys.toMutableList()
        Collections.shuffle(values)
        return values[0]
    }

    private fun chooseNextWord(current: Prefix, chain: Chain): String? {
        if (chain.containsKey(current)) {
            val choices = chain[current]?.toMutableList()
            Collections.shuffle(choices)
            return choices?.getOrNull(0)
        } else {
            return null
        }
    }

    private fun String.isEndOfSentence(): Boolean = this.endsWith(".") || this.endsWith("!") || this.endsWith("?")
}

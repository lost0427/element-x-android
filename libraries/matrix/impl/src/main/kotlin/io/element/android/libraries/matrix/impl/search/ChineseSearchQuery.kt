/*
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.search

import java.text.Normalizer
import java.util.Locale

/** Literal substring matching for Han queries, including mixed Chinese and Latin text. */
internal class ChineseSearchQuery private constructor(terms: List<String>) {
    private val chineseTerms = terms.filter { it.containsHan() }
    private val otherTerms = terms.filterNot { it.containsHan() }

    fun matches(body: String): Boolean {
        val normalizedBody = body.normalizeForSearch()
        if (!chineseTerms.all(normalizedBody::contains)) return false
        if (otherTerms.isEmpty()) return true
        val bodyWords = nonHanWords.findAll(normalizedBody).map { it.value }.toSet()
        return otherTerms.all(bodyWords::contains)
    }

    companion object {
        private val words = Regex("[\\p{L}\\p{N}]+")
        private val nonHanWords = Regex("[\\p{L}\\p{N}&&[^\\p{IsHan}]]+")

        fun from(query: String): ChineseSearchQuery? {
            if (!query.containsHan()) return null
            val terms = words.findAll(query.normalizeForSearch()).map { it.value }.distinct().toList()
            return terms.takeIf { it.isNotEmpty() }?.let(::ChineseSearchQuery)
        }
    }
}

private fun String.normalizeForSearch(): String = Normalizer.normalize(this, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

private fun String.containsHan(): Boolean = codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }

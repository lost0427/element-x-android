/*
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChineseSearchQueryTest {
    @Test
    fun `Chinese keywords match inside continuous sentences and long messages`() {
        val query = ChineseSearchQuery.from("下午")!!
        assertThat(query.matches("今天下午开会")).isTrue()
        assertThat(query.matches("很长的聊天记录".repeat(100) + "下午开会")).isTrue()
        assertThat(query.matches("今天上午开会")).isFalse()
    }

    @Test
    fun `all keywords are required and punctuation separates them`() {
        val query = ChineseSearchQuery.from("下午，开会")!!
        assertThat(query.matches("下午三点开会")).isTrue()
        assertThat(query.matches("下午出去吃饭")).isFalse()
        assertThat(query.matches("今天开会")).isFalse()
    }

    @Test
    fun `Chinese phrase order remains literal`() {
        val query = ChineseSearchQuery.from("下午开会")!!
        assertThat(query.matches("今天下午开会讨论方案")).isTrue()
        assertThat(query.matches("下午三点开会")).isFalse()
    }

    @Test
    fun `mixed queries normalize case and full width on both sides`() {
        val query = ChineseSearchQuery.from("会议 ＡＰＩ１２")!!
        assertThat(query.matches("会议讨论 Api12")).isTrue()
        assertThat(query.matches("会议讨论 Ａｐｉ１２")).isTrue()
        assertThat(query.matches("会议讨论 API13")).isFalse()
    }

    @Test
    fun `supplementary Han characters and single characters are supported`() {
        assertThat(ChineseSearchQuery.from("𠮷")!!.matches("𠮷野家")).isTrue()
        assertThat(ChineseSearchQuery.from("会")!!.matches("下午开会")).isTrue()
    }

    @Test
    fun `search syntax is never interpreted as an operator`() {
        val query = ChineseSearchQuery.from("中文 OR https://example.org")!!
        assertThat(query.matches("中文 or https://example.org")).isTrue()
        assertThat(query.matches("中文 https://example.org")).isFalse()
    }

    @Test
    fun `Latin keywords match beside Chinese but not inside other Latin words`() {
        val query = ChineseSearchQuery.from("会议 API")!!
        assertThat(query.matches("会议讨论API接口")).isTrue()
        assertThat(query.matches("会议讨论apis接口")).isFalse()
    }

    @Test
    fun `non Chinese queries keep using the SDK tokenizer`() {
        listOf("", " ", "hello world", "fotoğrafa bak", "📷", "⺅", "https://example.org").forEach {
            assertThat(ChineseSearchQuery.from(it)).isNull()
        }
    }
}

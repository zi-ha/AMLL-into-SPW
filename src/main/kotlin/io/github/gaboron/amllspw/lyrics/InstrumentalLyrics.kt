package io.github.gaboron.amllspw.lyrics

import java.text.Normalizer
import java.util.Locale

/** Only a completed empty document or a whole-document notice is an instrumental page. */
internal object InstrumentalLyrics {
    private val separators = Regex("[\\s\\p{P}\\p{Z}\\uFEFF]+")

    // Real lyric files often place the notice after a credit block.
    private val credit = Regex(
        "^(?:作[词詞]|作曲|编曲|編曲|制作人|製作人|制作|製作|出品人|出品|监制|監製|" +
            "混音|母带|母帶|录音|錄音|和声|和聲|吉他|贝斯|鼓|键盘|鍵盤|弦乐|弦樂|" +
            "发行|發行|统筹|統籌|企划|企劃|策划|策劃|宣传|宣傳|版权|版權|" +
            "lyrics?|music|op|sp|词|詞|曲|producer|composer|lyricist|arranger|mixing|mastering|" +
            "vocals?|guitars?|bass|drums?|keyboards?|strings?|label|publisher)" +
            "(?:\\s*[:：]|\\s+)|" +
            "^(?:produced|written|composed|arranged|mixed|mastered|recorded|performed|released)\\s+by\\b",
        RegexOption.IGNORE_CASE,
    )
    private val chineseNotice = Regex(
        "(?:(?:此|本)(?:歌曲?|[乐樂]曲|曲)(?:[为為])?(?:[没沒]有)?(?:填[词詞])?(?:的)?)?" +
            "(?:(?:纯音乐|純音樂|dj音乐|dj音樂|伴奏|无人声|無人聲|无歌词|無歌詞|没有歌词|沒有歌詞|暂无歌词|暫無歌詞)" +
            "(?:版(?:本)?)?)+(?:[请請]?您?欣[赏賞])?",
    )
    private val englishNotice = Regex(
        "(?:(?:thisis)?(?:an?)?(?:instrumental|offvocal)(?:music|version|track|offvocal)*" +
            "(?:pleaseenjoy|enjoy)?|(?:no|without)lyrics?|(?:no|without)vocals?)+",
    )

    fun matches(document: LyricDocument): Boolean {
        val lines = document.lines.mapNotNull { line ->
            val raw = line.text.trim()
            if (raw.isEmpty()) return@mapNotNull null
            val normalized = separators.replace(Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT), "")
            if (normalized.isEmpty()) null else raw to normalized
        }
        if (lines.isEmpty()) return true
        val credits = lines.map { (raw, _) -> credit.containsMatchIn(raw) }
        if (credits.all { it }) return true
        val content = lines.indices.filter { !credits[it] }.map { lines[it].second }
        if (content.isEmpty()) return true
        if (content.all { chineseNotice.matches(it) || englishNotice.matches(it) }) return true
        // Notices are occasionally split over several lines by the lyric source.
        val joined = content.joinToString("")
        return chineseNotice.matches(joined) || englishNotice.matches(joined)
    }
}

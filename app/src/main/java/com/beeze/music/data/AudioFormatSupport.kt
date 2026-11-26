package com.beeze.music.data

import android.media.MediaCodecList

/**
 * 音频格式可播性检测：容器层按 media3 内置解析器静态判断，解码层动态查询系统 MediaCodec。
 * 判断规则是「已知不可播的判死，已知可播的放行，未知容器乐观放行（失败由播放时自动跳过兜底）」。
 */
object AudioFormatSupport {

    /** media3 1.5.1 没有解析器、或加密私有的格式，任何设备都播不了 */
    private val UNSUPPORTED_EXTENSIONS = setOf(
        "ape", "wma", "wmv", "dsf", "dff", "wv", "tak", "mpc", "mpp", "mp+",
        "ra", "rm", "ram", "mid", "midi", "smf", "aif", "aiff", "aifc", "caf",
        "mlp", "thd", "dts", "dtshd", "m4p", "oma", "aa", "aax",
        // 流媒体平台的加密格式
        "ncm", "qmc0", "qmc2", "qmc3", "qmcflac", "qmcogg", "mflac", "mflac0", "mgg", "kgm", "kgma",
    )

    /** 容器内编码不固定（可能是 AAC 也可能是 ALAC 等），扩展名判不出来，乐观放行 */
    private val OPAQUE_CONTAINER_EXTENSIONS = setOf("m4a", "m4b", "mp4", "mka", "mkv", "webm")

    /** 容器固定但编码依赖系统解码器的格式：扩展名 → 可能的编码 MIME（任一存在即可播） */
    private val CODEC_CHECK_EXTENSIONS = mapOf(
        "flac" to listOf("audio/flac"),
        "ogg" to listOf("audio/vorbis", "audio/opus", "audio/flac"),
        "oga" to listOf("audio/vorbis", "audio/opus", "audio/flac"),
        "opus" to listOf("audio/opus"),
        "amr" to listOf("audio/3gpp"),
        "awb" to listOf("audio/amr-wb"),
        "ac3" to listOf("audio/ac3"),
        "ec3" to listOf("audio/eac3"),
        "eac3" to listOf("audio/eac3"),
        "ac4" to listOf("audio/ac4"),
        "mp3" to listOf("audio/mpeg"),
        "aac" to listOf("audio/mp4a-latm"),
        "wav" to listOf("audio/raw"),
    )

    /** 系统可用的音频解码器 MIME 集合，懒加载一次后常驻 */
    private val decoderMimes: Set<String> by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filter { !it.isEncoder }
                .flatMap { it.supportedTypes.asSequence() }
                .filter { it.startsWith("audio/") }
                .map { it.lowercase() }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun isSupported(path: String): Boolean {
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return true
        if (extension in UNSUPPORTED_EXTENSIONS) return false
        if (extension in OPAQUE_CONTAINER_EXTENSIONS) return true
        val mimes = CODEC_CHECK_EXTENSIONS[extension] ?: return true
        return mimes.any { it in decoderMimes }
    }

    /** 格式标签（如 "APE"），用于管理页展示 */
    fun formatLabel(path: String): String =
        path.substringAfterLast('.', "").uppercase()
}

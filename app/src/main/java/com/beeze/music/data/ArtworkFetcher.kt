package com.beeze.music.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

/**
 * 封面 Fetcher：先查 MediaStore 专辑封面表，查不到再从音频文件提取内嵌封面。
 * 新机型上封面表经常为空（封面只内嵌在文件里），必须两步兜底。
 */
class SongArtworkFetcher(
    private val context: Context,
    private val data: SongArtwork,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = readAlbumArtTable() ?: readEmbeddedPicture()
            ?: throw IllegalStateException("该音频没有封面")
        val bitmap = decodeScaled(bytes, TARGET_SIZE)
            ?: throw IllegalStateException("封面解码失败")
        return DrawableResult(
            drawable = bitmap.toDrawable(context.resources),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    /** 第一步：MediaStore 专辑封面表 */
    private fun readAlbumArtTable(): ByteArray? {
        if (data.albumId <= 0) return null
        return try {
            val uri = ContentUris.withAppendedId(ALBUM_ART_BASE_URI, data.albumId)
            context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes() }
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    /** 第二步：用 MediaMetadataRetriever 提取文件内嵌封面 */
    private fun readEmbeddedPicture(): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, data.contentUri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    /** 解码并降采样到目标尺寸，避免大图占内存 */
    private fun decodeScaled(bytes: ByteArray, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize &&
            bounds.outHeight / (sample * 2) >= maxSize
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    class Factory(private val context: Context) : Fetcher.Factory<SongArtwork> {
        override fun create(data: SongArtwork, options: Options, imageLoader: ImageLoader): Fetcher =
            SongArtworkFetcher(context, data)
    }

    private companion object {
        const val TARGET_SIZE = 512
    }
}

package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ReadingActivity : AppCompatActivity() {
    private lateinit var titleText: TextView
    private lateinit var contentText: TextView
    private lateinit var scrollView: NestedScrollView
    private lateinit var serverUrl: String
    private lateinit var bookName: String
    private lateinit var bookUrl: String
    private val client = HttpClientSingleton.client

    private var chapterList = JSONArray()
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reading)

        titleText   = findViewById(R.id.readingTitle)
        contentText = findViewById(R.id.readingContent)
        scrollView  = findViewById(R.id.scrollView)

        bookName = intent.getStringExtra("bookName") ?: "未知书名"
        val author = intent.getStringExtra("author") ?: "未知作者"
        serverUrl = intent.getStringExtra("serverUrl") ?: ""
        bookUrl   = intent.getStringExtra("url")       ?: ""
        titleText.text = "$bookName — $author"

        if (serverUrl.isEmpty() || bookUrl.isEmpty()) {
            Toast.makeText(this, "缺少必要参数", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 滚动翻页：顶部 prepend 上一章，底部 append 下一章
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // 顶部：加载并 prepend
            if (scrollY == 0 && currentIndex > 0) {
                currentIndex--
                loadChapter(currentIndex, prepend = true)
            }
            // 底部：加载并 append
            val child = scrollView.getChildAt(0)
            if (child != null && scrollY + scrollView.height >= child.height) {
                if (currentIndex < chapterList.length() - 1) {
                    currentIndex++
                    loadChapter(currentIndex, append = true)
                }
            }
        }

        loadChapterList()
    }

    private fun getChapterCacheFile(): File {
        val dir = File(cacheDir, bookName)
        dir.mkdirs()
        return File(dir, "chapters.json")
    }

    private fun getContentCacheFile(idx: Int): File {
        val dir = File(cacheDir, bookName)
        dir.mkdirs()
        return File(dir, "chapter_$idx.txt")
    }

    private fun loadChapterList() {
        val file = getChapterCacheFile()
        if (file.exists()) {
            try {
                chapterList = JSONArray(file.readText())
                loadChapter(currentIndex)  // 默认首次加载是替换模式
                return
            } catch (_: Exception) { }
        }
        val j = JSONObject().put("url", bookUrl)
        val body = j.toString().toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url("$serverUrl/getChapterList").post(body).build())
            .enqueue(object : Callback {
                override fun onFailure(c: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@ReadingActivity, "获取章节列表失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onResponse(c: Call, r: Response) {
                    val txt = r.body?.string() ?: ""
                    runOnUiThread {
                        try {
                            chapterList = JSONObject(txt).getJSONArray("data")
                            file.writeText(chapterList.toString())
                            loadChapter(currentIndex)
                        } catch (e: Exception) {
                            Toast.makeText(this@ReadingActivity, "解析章节列表失败", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
    }

    /**
     * @param index    要加载的章节索引
     * @param prepend  是否将本章内容插到前面（上翻页场景）
     * @param append   是否将本章内容追加到后面（下翻页场景）
     */
    private fun loadChapter(index: Int, prepend: Boolean = false, append: Boolean = false) {
        val cache = getContentCacheFile(index)
        if (cache.exists()) {
            val txt = cache.readText()
            runOnUiThread {
                when {
                    prepend -> {
                        // 保留原始高度，prepend 后滚动到新加入部分末尾
                        val oldHeight = contentText.height
                        contentText.text = "第${index+1}章\n\n$txt\n\n" + contentText.text
                        scrollView.post { scrollView.scrollTo(0, contentText.height - oldHeight) }
                    }
                    append -> {
                        contentText.append("\n\n第${index+1}章\n\n$txt")
                    }
                    else -> {
                        contentText.text = "第${index+1}章\n\n$txt"
                        scrollView.scrollTo(0, 0)
                    }
                }
            }
            return
        }
        val j = JSONObject().put("url", bookUrl).put("index", index)
        val body = j.toString().toRequestBody("application/json".toMediaType())
        client.newCall(Request.Builder().url("$serverUrl/getBookContent").post(body).build())
            .enqueue(object : Callback {
                override fun onFailure(c: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@ReadingActivity, "获取章节失败：${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onResponse(c: Call, r: Response) {
                    val txt = r.body?.string() ?: ""
                    try {
                        val content = JSONObject(txt).getString("data")
                        cache.writeText(content)
                        runOnUiThread {
                            when {
                                prepend -> {
                                    val oldH = contentText.height
                                    contentText.text = "第${index+1}章\n\n$content\n\n" + contentText.text
                                    scrollView.post { scrollView.scrollTo(0, contentText.height - oldH) }
                                }
                                append -> {
                                    contentText.append("\n\n第${index+1}章\n\n$content")
                                }
                                else -> {
                                    contentText.text = "第${index+1}章\n\n$content"
                                    scrollView.scrollTo(0, 0)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@ReadingActivity, "解析章节失败", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
    }
}

package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

// 数据类：表示一本书，包括获取章节所需的 bookUrl
data class Book(
    val name: String,
    val author: String,
    val durChapterTime: Long,
    val coverUrl: String?,
    val bookUrl: String
)

class BookshelfActivity : AppCompatActivity() {

    private val client = HttpClientSingleton.client
    private lateinit var listView: ListView
    private lateinit var scrollToTopButton: Button
    private lateinit var serverUrl: String

    // 用于点击跳转时获取对应书籍
    private lateinit var sortedList: List<Book>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookshelf)

        listView = findViewById(R.id.bookListView)
        scrollToTopButton = findViewById(R.id.scrollToTopButton)
        serverUrl = intent.getStringExtra("serverUrl").orEmpty()

        scrollToTopButton.setOnClickListener {
            listView.smoothScrollToPosition(0)
        }

        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "服务器地址不能为空", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        getBookshelf()
    }

    private fun getBookshelf() {
        val request = Request.Builder()
            .url("$serverUrl/getBookshelf")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@BookshelfActivity, "获取书架失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val json = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && !json.isNullOrEmpty()) {
                        try {
                            val jsonObject = JSONObject(json)
                            val isSuccess = jsonObject.optBoolean("isSuccess", true)
                            if (!isSuccess) {
                                val errorMsg = jsonObject.optString("errorMsg", "获取失败")
                                Toast.makeText(this@BookshelfActivity, errorMsg, Toast.LENGTH_LONG).show()
                                return@runOnUiThread
                            }

                            // 解析 data 数组
                            val dataArray = when {
                                jsonObject.has("data") && jsonObject.get("data") is JSONArray ->
                                    jsonObject.getJSONArray("data")
                                jsonObject.has("data") && jsonObject.get("data") is String ->
                                    JSONArray(jsonObject.getString("data"))
                                else -> JSONArray()
                            }
                            if (dataArray.length() == 0) {
                                Toast.makeText(this@BookshelfActivity, "书架为空", Toast.LENGTH_LONG).show()
                                listView.adapter = null
                                return@runOnUiThread
                            }

                            // 构造 Book 列表
                            val bookList = mutableListOf<Book>()
                            for (i in 0 until dataArray.length()) {
                                val obj = dataArray.getJSONObject(i)
                                val name = obj.optString("name", "未知书名")
                                val author = obj.optString("author", "未知作者")
                                val durTime = obj.optLong("durChapterTime", 0L)
                                val cover = obj.optString("coverUrl", null)
                                val url = obj.optString("bookUrl", "")
                                bookList.add(Book(name, author, durTime, cover, url))
                            }

                            // 按时间戳降序排序
                            sortedList = bookList.sortedByDescending { it.durChapterTime }

                            // 设置适配器
                            val adapter = object : ArrayAdapter<Book>(this@BookshelfActivity, R.layout.book_item, sortedList) {
                                override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                                    val view = convertView ?: layoutInflater.inflate(R.layout.book_item, parent, false)
                                    val book = getItem(pos)
                                    view.findViewById<TextView>(R.id.bookNameTextView).text = book?.name
                                    view.findViewById<TextView>(R.id.bookAuthorTextView).text = book?.author
                                    val coverView = view.findViewById<ImageView>(R.id.bookCoverImageView)
                                    if (book?.coverUrl != null) {
                                        val fullUrl = if (book.coverUrl.startsWith("http")) book.coverUrl
                                        else "$serverUrl${book.coverUrl}"
                                        Glide.with(this@BookshelfActivity).load(fullUrl).into(coverView)
                                    } else {
                                        coverView.setImageResource(android.R.color.darker_gray)
                                    }
                                    return view
                                }
                            }
                            listView.adapter = adapter

                            // 点击跳转到阅读界面
                            listView.setOnItemClickListener { _, _, position, _ ->
                                val sel = sortedList[position]
                                val intent = Intent(this@BookshelfActivity, ReadingActivity::class.java).apply {
                                    putExtra("bookName", sel.name)
                                    putExtra("author", sel.author)
                                    putExtra("url", sel.bookUrl)
                                    putExtra("serverUrl", serverUrl)
                                }
                                startActivity(intent)
                            }

                        } catch (e: Exception) {
                            Toast.makeText(this@BookshelfActivity, "解析数据失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@BookshelfActivity, "响应失败或数据为空", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}

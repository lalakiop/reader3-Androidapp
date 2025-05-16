package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var serverEditText: EditText
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button

    private val client = HttpClientSingleton.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverEditText = findViewById(R.id.serverEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)

        loginButton.setOnClickListener {
            login()
        }
    }

    private fun login() {
        val server = serverEditText.text.toString().trim()
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        val json = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("code", "")
            put("isLogin", true)
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("$server/login")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && result != null) {
                        // 可以根据服务端返回结果做更详细判断
                        val jsonResponse = JSONObject(result)
                        val isSuccess = jsonResponse.optBoolean("isSuccess", false)
                        if (isSuccess) {
                            Toast.makeText(this@MainActivity, "登录成功", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this@MainActivity, BookshelfActivity::class.java)
                            intent.putExtra("serverUrl", server)
                            startActivity(intent)
                        } else {
                            val errorMsg = jsonResponse.optString("errorMsg", "登录失败")
                            Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "登录失败: $result", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}

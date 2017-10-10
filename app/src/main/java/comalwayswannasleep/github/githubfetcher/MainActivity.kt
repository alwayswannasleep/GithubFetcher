package comalwayswannasleep.github.githubfetcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private val CALLBACK_URL = "http://my_callback.com/test"

        private val AUTH_URL = "https://github.com/login/oauth/authorize/?client_id=${BuildConfig.CLIENT_ID}&scope=repo"
        private val TOKEN_REQUEST_URL = "https://github.com/login/oauth/access_token?client_id=${BuildConfig.CLIENT_ID}&client_secret=${BuildConfig.CLIENT_SECRET}&code=%s"

        private val STORAGE_NAME = "test"
        private val STORAGE_TOKEN = "token_parameters"
    }

    private var storage: SharedPreferences? = null

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)

        fetchToken { tokenPath: String ->
            runOnUiThread {
                Toast.makeText(this@MainActivity, tokenPath, Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchToken(onCompletionResult: (tokenPath: String) -> Unit) {
        val token = storage!!.getString(STORAGE_TOKEN, "")
        if (!token.isEmpty()) {
            onCompletionResult(token)

            return
        }

        val container = RelativeLayout(this)
        val progress = ProgressBar(this)
        progress.isIndeterminate = true
        progress.layoutParams = RelativeLayout.LayoutParams(300, 300)

        val progressParams = progress.layoutParams
        (progressParams as? RelativeLayout.LayoutParams)?.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)

        container.addView(progress)
        val webView = WebView(this)

        webView.visibility = View.INVISIBLE

        container.addView(webView)

        val dialog = AlertDialog.Builder(this).setCancelable(false).setView(container).create()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (!request?.url.toString().startsWith(CALLBACK_URL)) {
                    return super.shouldOverrideUrlLoading(view, request)
                }

                val sessionCode = request?.url.toString().replace(regex = Regex("^.*code="), replacement = "")

                executor.submit({
                    val url = URL(String.format(TOKEN_REQUEST_URL, sessionCode))
                    val urlConnection = url.openConnection() as HttpURLConnection

                    if (urlConnection.responseCode != 200 && urlConnection.responseCode != 201) {
                        dialog.dismiss()
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Error receiving response: message - ${urlConnection.responseMessage}, code - ${urlConnection.responseCode}", Toast.LENGTH_LONG).show()
                        }

                        urlConnection.disconnect()

                        return@submit
                    }

                    val result: String

                    val buffer = StringBuilder()

                    BufferedReader(InputStreamReader(urlConnection.inputStream)).use {
                        while (true) {
                            val line = it.readLine() ?: break

                            buffer.append(line)
                        }
                    }

                    result = buffer.toString()

                    if (result.isEmpty()) {
                        dialog.dismiss()
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Empty response body.", Toast.LENGTH_LONG).show()
                        }

                        return@submit
                    }

                    storage!!.edit().putString(STORAGE_TOKEN, result).apply()
                    onCompletionResult(result)

                    dialog.dismiss()
                })

                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)

                webView.visibility = View.INVISIBLE
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                webView.visibility = View.VISIBLE
                progress.visibility = View.GONE
            }
        }

        webView.settings.javaScriptEnabled = true

        dialog.show()
        webView.loadUrl(AUTH_URL)
    }

    private fun loadRepositories() {
        // TODO add load repos request
    }
}

import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // 카카오 로그인 및 토큰 저장 필수 옵션
            userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: ""
                
                // 네가 찾은 그 API 주소가 호출되는 순간 헤더와 URL을 통째로 가로챔!
                if (url.contains("api.uplusmvno.com/umah/fo/care/use/v1/real-time")) {
                    val headersJson = JSONObject()
                    request?.requestHeaders?.forEach { (key, value) ->
                        headersJson.put(key, value)
                    }
                    val cookie = CookieManager.getInstance().getCookie("https://api.uplusmvno.com") ?: ""

                    val prefs = getSharedPreferences("AldotPrefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("API_URL", url)
                        .putString("HEADERS_JSON", headersJson.toString())
                        .putString("COOKIE", cookie)
                        .apply()

                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "알닷 연동 완료! 위젯을 새로고침하세요.", Toast.LENGTH_LONG).show()
                        finish() // 연동 끝나면 로그인 창 자동 닫기
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        // 접속 시 로그인 안 되어 있으면 자동으로 카카오 로그인 화면으로 이동함
        webView.loadUrl("https://www.uplusmvno.com/care/use/real-time")
    }
}

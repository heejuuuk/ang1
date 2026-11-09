import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.roundToInt

class AldotWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "ACTION_REFRESH_ALDOT") {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AldotWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_aldot)

        // 1. 우측 새로고침 버튼 누르면 즉시 갱신
        val refreshIntent = Intent(context, AldotWidgetProvider::class.java).apply {
            action = "ACTION_REFRESH_ALDOT"
        }
        val refreshPending = PendingIntent.getBroadcast(
            context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending)

        // 2. 위젯 본체 누르면 카카오 로그인(세션 갱신) 창 열기
        val loginIntent = Intent(context, LoginActivity::class.java)
        val loginPending = PendingIntent.getActivity(
            context, 1, loginIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, loginPending)

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.getSharedPreferences("AldotPrefs", Context.MODE_PRIVATE)
            // 기본값으로 네가 따온 URL 세팅
            val apiUrl = prefs.getString(
                "API_URL",
                "https://api.uplusmvno.com/umah/fo/care/use/v1/real-time?entrId=516924430376&xUserId=1100000149"
            )!!
            val headersStr = prefs.getString("HEADERS_JSON", "{}")!!
            val cookie = prefs.getString("COOKIE", "")!!

            val requestBuilder = Request.Builder().url(apiUrl)
            val headersJson = JSONObject(headersStr)
            headersJson.keys().forEach { key ->
                requestBuilder.addHeader(key, headersJson.getString(key))
            }
            if (cookie.isNotEmpty()) {
                requestBuilder.addHeader("Cookie", cookie)
            }

            try {
                val response = OkHttpClient().newCall(requestBuilder.build()).execute()
                val body = response.body?.string() ?: ""
                val root = JSONObject(body)

                if (root.optString("resultCode") == "0000") {
                    val dataList = root.getJSONArray("useDataList")
                    var mainAlloKB = 0.0
                    var mainUseKB = 0.0
                    var tetherAlloKB = 0.0
                    var tetherUseKB = 0.0

                    // useDataList 배열에서 본 데이터(LPZ1005093)와 테더링(LRZ0002794) 추출
                    for (i in 0 until dataList.length()) {
                        val item = dataList.getJSONObject(i)
                        val order = item.optInt("order")
                        if (order == 1) { // 월 기본 제공 데이터 (144GB)
                            mainAlloKB = item.optString("alloValue", "0").toDoubleOrNull() ?: 0.0
                            mainUseKB = item.optString("useValue", "0").toDoubleOrNull() ?: 0.0
                        } else if (order == 3) { // 테더링 (16GB)
                            tetherAlloKB = item.optString("alloValue", "0").toDoubleOrNull() ?: 0.0
                            tetherUseKB = item.optString("useValue", "0").toDoubleOrNull() ?: 0.0
                        }
                    }

                    // KB -> GB 변환 (1024 * 1024 = 1,048,576)
                    val totalGB = mainAlloKB / 1048576.0
                    val remainGB = (mainAlloKB - mainUseKB) / 1048576.0
                    val remainText = String.format("%.1fG / %.0fG", remainGB, totalGB)
                    val gaugePercent = if (totalGB > 0) ((remainGB / totalGB) * 100).roundToInt() else 0

                    // 테더링 계산
                    val tetherTotalGB = tetherAlloKB / 1048576.0
                    val tetherRemainGB = (tetherAlloKB - tetherUseKB) / 1048576.0
                    val tetherText = String.format("%.1fG / %.0fG", tetherRemainGB, tetherTotalGB)
                    val tetherPercent = if (tetherTotalGB > 0) ((tetherRemainGB / tetherTotalGB) * 100).roundToInt() else 0

                    // UI 업데이트 (네가 원하는 디자인 그대로 매핑)
                    views.setTextViewText(R.id.tv_main_data, remainText) // "137.8G / 144G"
                    views.setProgressBar(R.id.pb_main_gauge, 100, gaugePercent, false)
                    views.setTextViewText(R.id.tv_main_sub, "통 무제한 · 문 무제한")

                    views.setTextViewText(R.id.tv_tether_data, tetherText) // "16.0G / 16G"
                    views.setProgressBar(R.id.pb_tether_gauge, 100, tetherPercent, false)
                    views.setTextViewText(R.id.tv_tether_sub, "잔여 ${root.optInt("remainDay")}일")
                } else {
                    views.setTextViewText(R.id.tv_main_data, "터치해 로그인")
                }
            } catch (e: Exception) {
                views.setTextViewText(R.id.tv_main_data, "연동 필요(터치)")
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}

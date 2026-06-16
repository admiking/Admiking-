package com.example.api

import com.example.BuildConfig
import com.example.data.AppUsageRecord
import com.example.data.Habit
import com.example.data.Task
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

// --- Data Classes for Gemini Request/Response ---

data class Part(val text: String)
data class Content(val parts: List<Part>)
data class GenerateContentRequest(val contents: List<Content>)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): ResponseBody
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiClient {
    suspend fun getProductivityCoaching(
        tasks: List<Task>,
        habits: List<Habit>,
        usageRecords: List<AppUsageRecord>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getFallbackAdvice(tasks, habits, usageRecords)
        }

        val tasksListStr = tasks.joinToString("\n") { "- ${it.title} (تصنيف: ${it.category}, مكتملة: ${if (it.isCompleted) "نعم" else "لا"})" }
        val habitsListStr = habits.joinToString("\n") { "- ${it.name} (التكرار الحالي: ${it.streak} أيام)" }
        val usageStr = usageRecords.joinToString("\n") { "- ${it.appName}: ${it.durationMs / 60000} دقيقة" }

        val prompt = """
            أنت خبير إنتاجية شخصي ومستشار تنظيم الوقت لتطبيق "حياتي" للهواتف الذكية.
            يرجى تحليل روتين وبيانات المستخدم التالية وتقديم خطة زمنية ديناميكية للمهام، والوقت الأنسب للإنتاجية والتركيز، مع نصائح للتغلب على المشتتات باللغة العربية بأسلوب راقٍ وملهم.

            المهام المجدولة اليوم:
            $tasksListStr

            العادات المتبع تكرارها:
            $habitsListStr

            إحصائيات استخدام تطبيقات الهاتف المشتتة اليوم:
            $usageStr

            يرجى تنظيم استجابتك بوضوح تام، مقسمة إلى:
            1. **أفضل أوقات الإنتاجية اليومية المقترحة لك**: بناء على تحليل الاستخدام والمهام.
            2. **جدول زمني ديناميكي مقترح يومي (ساعات اليوم)**: لتوزيع المهام بالتناسب مع الصلاة والتركيز.
            3. **توصيات الذكاء الاصطناعي**: للحد من استخدام التطبيقات المشتتة وتنمية العادات.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            )
        )

        try {
            val responseBody = RetrofitClient.service.generateContent(apiKey, request)
            val jsonString = responseBody.string()
            val jsonObject = JSONObject(jsonString)
            val candidates = jsonObject.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text")
            text
        } catch (e: Exception) {
            e.printStackTrace()
            getFallbackAdvice(tasks, habits, usageRecords)
        }
    }

    suspend fun getFuturePlanEvaluation(
        quranBaseline: Int,
        tasks: List<Task>,
        habits: List<Habit>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getFallbackFuturePlan(quranBaseline)
        }

        val prompt = """
            بصفتك مستشار التنمية والارتقاء الشخصي الإسلامي والإنتاجية الذكية، نطلب منك تحسين "الخطة المستقبلية لتبني العادات والمهام" للمستخدم واقتراح "خطة تقويم وإصلاح زمنية" تهدف للارتقاء بقراءة وتلاوة القرآن الكريم بزيادة تدريجية دقيقة تقدر بالنسبة 10% شهرياً.

            المعطيات الحالية للمستخدم:
            - معدل قراءة القرآن الأولي الحالي (في اليوم): $quranBaseline صفحات.
            - الجدول المقدر للزيادة المقترحة (+10% شهرياً):
              * الشهر الأول: ${quranBaseline} صفحات
              * الشهر الثاني: ${String.format("%.1f", quranBaseline * 1.1)} صفحات
              * الشهر الثالث: ${String.format("%.1f", quranBaseline * 1.21)} صفحات
              * الشهر الرابع: ${String.format("%.1f", quranBaseline * 1.33)} صفحات
              * الشهر الخامس: ${String.format("%.1f", quranBaseline * 1.46)} صفحات
              * الشهر السادس: ${String.format("%.1f", quranBaseline * 1.61)} صفحات

            يرجى تقديم رد متكامل ومنظم ومصاغ باللغة العربية بأسلوب راقٍ وملهم ومؤثر لبيان أهمية قراءة القرآن والمثابرة، يحتوي على:
            1. **تحليل خطة الورد القرآني والارتقاء التدريجي لـ 10%**: خطوات عملية ومجربة يومياً وأسبوعياً لتثبيت زيادة الـ 10% شهرياً بذكاء ودون تراخٍ (مثال: توزيع القراءة على دبر الصلوات الخمس بزيادة سطر في كل صلاة).
            2. **كيفية تحسين وتنظيم الخطة المستقبلية اليومية للمستخدم**: كيفية موازنة العادات والعمل ودمج الورد القرآني ضمنها بنجاح وموازنة دقيقة.
            3. **خطة تقويمية وتوجيهات نفسية وروحية**: لتحسين الهمة وزيادة الالتزام والخشوع بورد القرآن الكريم المتنامي وتدبره.
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            )
        )

        try {
            val responseBody = RetrofitClient.service.generateContent(apiKey, request)
            val jsonString = responseBody.string()
            val jsonObject = JSONObject(jsonString)
            val candidates = jsonObject.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            val text = parts.getJSONObject(0).getString("text")
            text
        } catch (e: Exception) {
            e.printStackTrace()
            getFallbackFuturePlan(quranBaseline)
        }
    }

    private fun getFallbackFuturePlan(quranBaseline: Int): String {
        val m1 = quranBaseline
        val m2 = String.format("%.1f", quranBaseline * 1.1)
        val m3 = String.format("%.1f", quranBaseline * 1.21)
        val m4 = String.format("%.1f", quranBaseline * 1.33)
        val m5 = String.format("%.1f", quranBaseline * 1.46)
        val m6 = String.format("%.1f", quranBaseline * 1.61)

        return """
            📊 **تحليل خطة التقويم والارتقاء الرقمي التلقائي (التحليل المحلي):**

            نسعى في تطبيق "حياتي" لمساعدتك في بناء روتين إيماني متصاعد ومستدام. إليك خطتك التفصيلية المقترحة للتقويم والزيادة التدريجية بنسبة 10% شهرياً:

            ### 📈 جدول التقويم والورد الرقمي المتوقع (6 أشهر):
            - **الشهر الأول**: $m1 صفحات يومياً (الأساس الثابت وحضور القلب).
            - **الشهر الثاني**: $m2 صفحات يومياً (إضافة 10% - تثبيت العادة وتعميق التدبر).
            - **الشهر الثالث**: $m3 صفحات يومياً (إضافة 10% - استغلال فترات البكور بعد الفجر).
            - **الشهر الرابع**: $m4 صفحات يومياً (إضافة 10% - الموازنة والربط بصلوات الفريضة).
            - **الشهر الخامس**: $m5 صفحات يومياً (إضافة 10% - تهيئة النفس للتلاوة الطويلة والخشوع).
            - **الشهر السادس**: $m6 صفحات يومياً (إضافة 10% - مضاعفة الورد عن البداية بسلاسة تامة).

            ### 🛠️ منهجية خطة التقويم وخطوات التنفيذ العملية:
            1. **ستراتيجية التوزيع المجزأ**: لا تقرأ الورد دفعة واحدة، بل وزّع الصفحات المطلوبة بمقدار صفحة أو صفحتين دبر كل صلاة مكتوبة لتشعر بخفتها.
            2. **مبدأ الالتزام في البكور (ما بعد الفجر)**: احرص على قراءة 40% من مستهدفك اليومي بعد صلاة الفجر مباشرة، حيث يكون الذهن صافياً وخالياً من المشتتات والشاغلات اليومية.
            3. **نظام التعويض المرن**: إذا عجزت عن إكمال المستهدف في يومٍ ما، التزم بتعويض النقص في اليوم التالي أو في عطلة نهاية الأسبوع لتبقى في المسار الصحيح.
            4. **تقويم النية المترابط**: اجعل زيادة التلاوة عبادة متصلة بالتأمل لتنمية السكينة النفسية والتخلص من ضغوط العمل والحياة اليومية.
        """.trimIndent()
    }

    private fun getFallbackAdvice(
        tasks: List<Task>,
        habits: List<Habit>,
        usageRecords: List<AppUsageRecord>
    ): String {
        val totalTasks = tasks.size
        val completedTasks = tasks.count { it.isCompleted }
        
        return """
            **تحليل الإنتاجية التلقائي (محلي):**
            
            1. **أفضل أوقات الإنتاجية اليومية المقترحة لك**:
            - ننصحك بالتركيز الذهني العالي في الساعات الصباحية الأولى (بعد صلاة الفجر إلى الساعة 10:00 صباحاً)، فهي تمثل ذروة الإنتاجية الطبيعية لتجهيز المهام الهامة مثل: *${tasks.firstOrNull { !it.isCompleted }?.title ?: "مهامك العامة"}*.
            - فترة ما بعد الظهر والمساء خذ فترات راحة قصيرة وخصصها لمراجعة العادات اليومية.

            2. **جدول زمني ديناميكي مقترح يومي**:
            - **05:30 - 07:00**: قراءة الورد من القرآن الكريم والبدء بالمهام العميقة الأكثر صعوبة.
            - **09:00 - 12:00**: العمل على المهام الأساسية (*تم إكمال $completedTasks من أصل $totalTasks مهام*).
            - **13:00 - 15:30**: مراجعة المهام الإدارية الخفيفة وتتبع العادات.
            - **18:00 - 20:00**: فترات راحة وترفيه، مع غلق التطبيقات المشتتة لزيادة التركيز.

            3. **توصيات الذكاء الاصطناعي لموازنة حياتك**:
            - حاول خفض استخدام الهاتف المشتت لتوفير المزيد من الساعات للتركيز الذهني.
            - واصل بناء سلسلة التزام قوية لعاداتك اليومية: (${habits.joinToString { it.name }}).
        """.trimIndent()
    }
}

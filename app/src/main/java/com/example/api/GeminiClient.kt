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

    suspend fun predictHabitSolidification(
        habitName: String,
        category: String,
        targetDurationMinutes: Int
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getFallbackSolidification(habitName, category, targetDurationMinutes)
        }

        val prompt = """
            بصفتك مستشار تخطيط وهندسة العادات وبناء الذات باستخدام الذكاء الاصطناعي، نريد منك التنبؤ بـ "موجة التثبيت" أو "المدة المتوقعة لتثبيت العادة" (Habit Formation Period) لتصبح سلوكاً تلقائياً راسخاً.
            
            معطيات العادة:
            - اسم العادة: $habitName
            - التصنيف: $category
            - مدة الممارسة المستهدفة يومياً: $targetDurationMinutes دقيقة
            - التكرار: يومي
            
            المطلوب:
            تحديد عدد الأيام المتوقعة بشكل دقيق لتثبيت هذه العادة (عادة ما بين 18 إلى 90 يوماً حسب الصعوبة والالتزام)، وتقديم نصيحة وحافز علمي موجز وجميل باللغة العربية حول كيفية المحافظة عليها بذكاء.
            
            تنبيه هام جداً:
            يجب أن تبدأ إجابتك بكتابة عدد الأيام فقط في السطر الأول كرقم صحيح (مثال: 45)، ثم يتبعه سطر جديد فارغ، ثم الشرح العلمي والنصيحة التحفيزية بشكل منسق وجميل. لا تضع أي كلام آخر في السطر الأول سوى الرقم المجرّد.
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
            val text = parts.getJSONObject(0).getString("text").trim()
            
            val lines = text.split("\n")
            val daysStr = lines.firstOrNull()?.trim()?.replace(Regex("[^0-9]"), "") ?: ""
            val days = daysStr.toIntOrNull() ?: 21
            val explanation = lines.drop(1).joinToString("\n").trim()
            
            Pair(days, explanation.ifEmpty { "إستمر لمدة $days يوماً لبناء مسارات عصبية جديدة في الدماغ تدعم هذه العادة وتجعلها تلقائية." })
        } catch (e: Exception) {
            e.printStackTrace()
            getFallbackSolidification(habitName, category, targetDurationMinutes)
        }
    }

    private fun getFallbackSolidification(
        habitName: String,
        category: String,
        targetDurationMinutes: Int
    ): Pair<Int, String> {
        val baseDays = when (category) {
            "عبادة" -> 30
            "صحّة", "صحة", "رياضة" -> 45
            "تعلم", "دراسة" -> 21
            "عمل" -> 35
            else -> 21
        }
        val durationPenalty = if (targetDurationMinutes > 30) 15 else if (targetDurationMinutes > 15) 7 else 0
        val finalDays = baseDays + durationPenalty
        
        val adviceStr = """
            سوف تحتاج لحوالي $finalDays يوماً لتثبيت هذه العادة («$habitName»).
            
            *توصية الذكاء الاصطناعي (تحليل محلي):*
            يبدأ الدماغ في بناء وتدعيم الممرات العصبية الجديدة لهذه العادة بمجرد الاستمرار لـ 21 يوماً بشكل متصل. بما أن مدة العادة المستهدفة هي $targetDurationMinutes دقيقة، ننصحك بالبدء بـ 5 دقائق فقط في الأيام الخمسة الأولى للتغلب على مقاومة العقل الباطن (التسويف)، ثم تدرج بزيادة دقيقة كل يومين لضمان الاستمرارية الطويلة والنجاح في بلوغ هدفك.
        """.trimIndent()
        
        return Pair(finalDays, adviceStr)
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

    suspend fun getScreenTimeAdvice(
        usageRecords: List<AppUsageRecord>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext getFallbackScreenTimeAdvice(usageRecords)
        }

        val usageStr = usageRecords.joinToString("\n") { "- ${it.appName}: ${it.durationMs / 60000} دقيقة" }

        val prompt = """
            أنت مستشار خبير ومحترف في الصحة الرقمية والتركيز، ومسؤول الإرشاد لرفاهية وقت الشاشة في تطبيق "حياتي" الإسلامي والإنتاجي.
            قم بتحليل إحصائيات استخدام الهاتف المشتت التالية لهذا اليوم، وقدم نصائح مخصصة، عملية، وذكية للحد من استخدام الهاتف وتقليص المشتتات، وربط ذلك بالإنتاجية وحفظ الأوقات والعبادة باللغة العربية بأسلوب راقٍ، ملهم، وواضح.

            إحصائيات استخدام تطبيقات الهاتف اليوم:
            $usageStr

            المطلوب منك صياغة رد يحتوي على الأقسام التالية بوضوح وبطريقة تفاعلية ملهمة:
            1. 📊 **تشخيص حالة الاستخدام الحالي**: تقييم علمي لمستوى التشتت وتأثيره على صفاء الذهن بناءً على الأرقام أعلاه.
            2. 💡 **خطة التحرر السريعة (توصيات مخصصة لكل تطبيق)**: نصائح ذكية وعملية مخصصة لكل تطبيق من التطبيقات الأكثر استهلاكاً لوقتك للحد من تصفحه (مثلاً: تفعيل تذكيرات النوم، استخدام نمط الشاشة الرمادية، حذف اختصارات التطبيق).
            3. 🕋 **بدائل روحية وإنتاجية هادفة**: اقترح بدائل تعمر بها وقتك بدلاً من التمرير اللانهائي (مثل: تلاوة ورد القرآن، أذكار، رياضة، قراءة كتب هادفة).
            4. 🔔 **رسالة تذكيرية قصيرة للإشعارات**: صغ رسالة تذكيرية قصيرة وموجزة جداً (أقل من 60 حرفاً) يمكن إرسالها للمستخدم كإشعار على هاتفه لتنبيهه وإيقاظ همته لتقليل الاستخدام الآن. تبدأ بـ "🔔 [رسالة التذكير]" في سطر منفصل وبسيط.
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
            getFallbackScreenTimeAdvice(usageRecords)
        }
    }

    private fun getFallbackScreenTimeAdvice(usageRecords: List<AppUsageRecord>): String {
        val totalMinutes = usageRecords.sumOf { it.durationMs } / 60000
        val topApps = usageRecords.sortedByDescending { it.durationMs }.take(3)
        val topAppsStr = topApps.joinToString("، ") { "${it.appName} (${it.durationMs / 60000} د)" }
        
        return """
            📊 **تشخيص حالة الاستخدام الحالي (التحليل المحلي):**
            - إجمالي وقت الشاشة اليوم هو **${totalMinutes / 60} ساعة و ${totalMinutes % 60} دقيقة**.
            - التطبيقات الأكثر استهلاكاً لوقتك هي: **$topAppsStr**.
            - مستوى التشتت: ${if (totalMinutes > 150) "حرج جداً! هاتفك يلتهم يومك بنهم." else if (totalMinutes > 60) "متوسط. انتبه قبل الانزلاق في الفخ الرقمي." else "ممتاز! تحكم واعٍ ورائع لوقتك."}

            💡 **خطة التحرر السريعة (توصيات مخصصة لكل تطبيق):**
            ${topApps.joinToString("\n") { app ->
                val mins = app.durationMs / 60000
                val appAdvice = when {
                    app.appName.contains("يوتيوب", true) -> "يوتيوب يسحبك عبر ميزة الفيديوهات القصيرة (Shorts) والتشغيل التلقائي. قم بتعطيل التشغيل التلقائي وحدد حداً يومياً بـ 20 دقيقة فقط."
                    app.appName.contains("تيك", true) || app.appName.contains("تيك توك", true) -> "تيك توك مصمم للإدمان عبر التمرير اللانهائي المبرمج عصبياً. ننصحك باستخدام نمط الشاشة الرمادية لتقليل جاذبية الألوان أو حظر التطبيق مؤقتاً."
                    app.appName.contains("إنستغرام", true) || app.appName.contains("انستقرام", true) || app.appName.contains("انستجرام", true) -> "إنستغرام يزيد من مقارنة الذات بالآخرين ويبدد انتباهك. احرص على تصفحه عبر المتصفح فقط أو احذف اختصاره من الشاشة الرئيسية لتصعيب الوصول إليه."
                    app.appName.contains("واتساب", true) -> "واتساب يسرق الوقت عبر المحادثات غير الهامة والجروبات. كتم التنبيهات غير الضرورية وتحديد وقت محدد للرد والمراجعة أسبوعياً."
                    else -> "تطبيق ${app.appName} يستهلك الكثير من وقتك ($mins دقيقة). اسأل نفسك قبل فتحه: هل هناك حاجة ملحة الآن أم هو هرب لملء الفراغ؟"
                }
                "- **${app.appName}**: $appAdvice"
            }}

            🕋 **بدائل روحية وإنتاجية هادفة:**
            1. **الورد القرآني**: استبدل 15 دقيقة من التصفح بقراءة صفحتين من القرآن الكريم (تحقق لك 2000 حسنة على الأقل).
            2. **مؤقت التركيز**: قم فوراً بتفعيل نمط التركيز في تطبيق حياتي لمدة 25 دقيقة لإنجاز مهمة معطلة.
            3. **الاستغفار والذكر**: استغل فترات الانتقال بذكر الله بدلاً من فحص الهاتف اللاواعي.

            🔔 **رسالة تذكيرية قصيرة للإشعارات:**
            الوقت أنفاس لا تعود.. ضع هاتفك الآن وعش لحظتك بوعي 🌾
        """.trimIndent()
    }
}

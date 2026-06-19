package com.example.util

object LangHelper {
    private var currentLang = "ar"

    fun setLanguage(lang: String) {
        currentLang = lang
    }

    fun getLanguage(): String = currentLang

    fun tr(key: String): String {
        if (currentLang == "ar") {
            return arToEnMap[key]?.first ?: key
        }
        val entry = arToEnMap[key]
        return entry?.second ?: key
    }

    // Map of Arabic Text to Pair of (Arabic text, English translation)
    private val arToEnMap = mapOf<String, Pair<String, String>>(
        "الرئيسية" to Pair("الرئيسية", "Home"),
        "العادات" to Pair("العادات", "Habits"),
        "مراقب التركيز" to Pair("مراقب التركيز", "Focus Tracker"),
        "القرآن والصلاة" to Pair("القرآن والصلاة", "Quran & Prayer"),
        "مستشار الذكاء" to Pair("مستشار الذكاء", "AI Advisor"),
        
        // Navigation / Tabs
        "القرآن" to Pair("القرآن", "Quran"),
        "أذكار" to Pair("أذكار", "Azkar"),
        "تسبيح" to Pair("تسبيح", "Tasbih"),
        "الفقه" to Pair("الفقه", "Fiqh"),
        "الإعدادات" to Pair("الإعدادات", "Settings"),
        
        // Home Screen
        "حياتي" to Pair("حياتي", "Hayaty"),
        "الملف الشخصي" to Pair("الملف الشخصي", "Profile"),
        "صباح الخير والبركة والنشاط! ☀️" to Pair("صباح الخير والبركة والنشاط! ☀️", "Good morning with blessings and energy! ☀️"),
        "مساء الخير والهدوء والبر والتقوى! 🌅" to Pair("مساء الخير والهدوء والبر والتقوى! 🌅", "Good evening with tranquility and piety! 🌅"),
        "مساء الطمأنينة والذكر والقيام! 🌙" to Pair("مساء الطمأنينة والذكر والقيام! 🌙", "Evening of peace, remembrance and prayers! 🌙"),
        "طاب يومك بكل خير وسعادة! ✨" to Pair("طاب يومك بكل خير وسعادة! ✨", "Have a wonderful day filled with goodness! ✨"),
        "«يا حي يا قيوم برحمتك أستغيث..» ابدأ يومك بهمة ونشاط وعزيمة." to Pair(
            "«يا حي يا قيوم برحمتك أستغيث..» ابدأ يومك بهمة ونشاط وعزيمة.",
            "\"O Ever Healing, O Self-Sustaining, by Your mercy I seek help...\" Start your day with vigor and determination."
        ),
        "صلاة المساء وأذكار الغروب تبث الطمأنينة في قلبك وعائلتك." to Pair(
            "صلاة المساء وأذكار الغروب تبث الطمأنينة في قلبك وعائلتك.",
            "Evening prayers and sunset Azkar bring tranquility to your heart and family."
        ),
        "سكينة الليل فرصة للذكر، قراءة الورد، وصلاة ركعتين في جوف الليل." to Pair(
            "سكينة الليل فرصة للذكر، قراءة الورد، وصلاة ركعتين في جوف الليل.",
            "The stillness of the night is an opportunity for remembrance, Quran reading, and night prayer."
        ),
        "اذكر الله في كل وقت وحين، وتوكل عليه في كل أمورك." to Pair(
            "اذكر الله في كل وقت وحين، وتوكل عليه في كل أمورك.",
            "Remember Allah at all times, and rely on Him in all your affairs."
        ),
        
        "أوقات الصلاة" to Pair("أوقات الصلاة", "Prayer Times"),
        "المهام اليومية" to Pair("المهام اليومية", "Daily Tasks"),
        "العادات اليومية الـنشطة" to Pair("العادات اليومية الـنشطة", "Active Daily Habits"),
        "استخدام الهاتف اليوم" to Pair("استخدام الهاتف اليوم", "Phone Usage Today"),
        "تقويم الالتزام لشهر" to Pair("تقويم الالتزام لشهر", "Commitment Heatmap for"),
        "اليوم:" to Pair("اليوم:", "Today:"),
        
        "تعديل" to Pair("تعديل", "Edit"),
        "تحديث البيانات" to Pair("تحديث البيانات", "Refresh Data"),
        "البحث عن مدينة" to Pair("البحث عن مدينة", "Search for City"),
        "اختر المدينة والدولة" to Pair("اختر المدينة والدولة", "Select City and Country"),
        "المدينة:" to Pair("المدينة:", "City:"),
        "الدولة:" to Pair("الدولة:", "Country:"),
        "حفظ" to Pair("حفظ", "Save"),
        "إغلاق" to Pair("إغلاق", "Close"),
        "جاري جلب أوقات الصلاة من الإنترنت..." to Pair("جاري جلب أوقات الصلاة من الإنترنت...", "Loading prayer times from internet..."),
        
        // Prayer Times Detail
        "الفجر" to Pair("الفجر", "Fajr"),
        "الشروق" to Pair("الشروق", "Sunrise"),
        "الظهر" to Pair("الظهر", "Dhuhr"),
        "العصر" to Pair("العصر", "Asr"),
        "المغرب" to Pair("المغرب", "Maghrib"),
        "العشاء" to Pair("العشاء", "Isha"),
        
        // Task List & Add task
        "إضافة مهمة جديدة" to Pair("إضافة مهمة جديدة", "Add New Task"),
        "عنوان المهمة" to Pair("عنوان المهمة", "Task Title"),
        "التصنيف" to Pair("التصنيف", "Category"),
        "عام" to Pair("عام", "General"),
        "دراسة" to Pair("دراسة", "Study"),
        "عمل" to Pair("عمل", "Work"),
        "رياضة" to Pair("رياضة", "Sport"),
        "ترفيه" to Pair("ترفيه", "Entertainment"),
        "أخرى" to Pair("أخرى", "Other"),
        "وقت التنفيذ" to Pair("وقت التنفيذ", "Execution Time"),
        "ملاحظات (اختياري)" to Pair("ملاحظات (اختياري)", "Notes (Optional)"),
        "إضافة المهمة" to Pair("إضافة المهمة", "Add Task"),
        "إلغاء" to Pair("إلغاء", "Cancel"),
        "خطأ" to Pair("خطأ", "Error"),
        "عنوان المهمة لا يمكن أن يكون فارغاً!" to Pair("عنوان المهمة لا يمكن أن يكون فارغاً!", "Task title cannot be empty!"),
        
        "الكل" to Pair("الكل", "All"),
        "المتبقية" to Pair("المتبقية", "Remaining"),
        "المنجزة" to Pair("المنجزة", "Completed"),
        "أضف مهمة سريعة واضغط Enter..." to Pair("أضف مهمة سريعة واضغط Enter...", "Add quick task and press Enter..."),
        "لا توجد مهام مطابقة للمرشح الحالي. 🎉" to Pair("لا توجد مهام مطابقة للمرشح الحالي. 🎉", "No tasks match the current filter. 🎉"),
        
        // Habits Screen
        "إدارة العادات اليومية" to Pair("إدارة العادات اليومية", "Manage Daily Habits"),
        "تحفيز ذكي من جيمي متاح في صفحة المستشار 🤖" to Pair("تحفيز ذكي من جيمي متاح في صفحة المستشار 🤖", "Smart motivation from Gemini is available on the Advisor page 🤖"),
        "العادات الحالية:" to Pair("العادات الحالية:", "Current Habits:"),
        "إضافة عادة جديدة" to Pair("إضافة عادة جديدة", "Add New Habit"),
        "اسم العادة" to Pair("اسم العادة", "Habit Name"),
        "النوع / التصنيف" to Pair("النوع / التصنيف", "Category"),
        "أيقونة رمزية (Emoji)" to Pair("أيقونة رمزية (Emoji)", "Icon (Emoji)"),
        "وقت التذكير اليومي (اختياري)" to Pair("وقت التذكير اليومي (اختياري)", "Reminder Time (Optional)"),
        "ربط بفتح تطبيق أندرويد (تلقائي)" to Pair("ربط بفتح تطبيق أندرويد (تلقائي)", "Link to Android App Auto-Open"),
        "شرح فائدة العادة بالذكاء الاصطناعي (Gemini):" to Pair("شرح فائدة العادة بالذكاء الاصطناعي (Gemini):", "AI benefits explanation (Gemini):"),
        "انقر للإنشاء عبر الذكاء الاصطناعي ✨" to Pair("انقر للإنشاء عبر الذكاء الاصطناعي ✨", "Click to generate with AI ✨"),
        "حفظ العادة" to Pair("حفظ العادة", "Save Habit"),
        "تفاصيل العادة" to Pair("تفاصيل العادة", "Habit Details"),
        "تعديل عادة" to Pair("تعديل عادة", "Edit Habit"),
        "هل أنت متأكد من حذف هذه العادة؟" to Pair("هل أنت متأكد من حذف هذه العادة؟", "Are you sure you want to delete this habit?"),
        "تأكيد الحذف" to Pair("تأكيد الحذف", "Confirm Delete"),
        "حذف" to Pair("حذف", "Delete"),
        
        // Focus Screen
        "مراقب التركيز والحد من المشتتات 🎯" to Pair("مراقب التركيز والحد من المشتتات 🎯", "Focus Monitor & Distraction Blocker 🎯"),
        "هذا الوضع يساعدك على الانفصال عن هاتفك والتركيز على العبادات والمهام الهامة عبر حظر التطبيقات المحددة." to Pair(
            "هذا الوضع يساعدك على الانفصال عن هاتفك والتركيز على العبادات والمهام الهامة عبر حظر التطبيقات المحددة.",
            "This mode helps you disconnect from your phone and focus on worship and key tasks by blocking selected apps."
        ),
        "اختر التطبيقات المُراد حظرها أثناء جلسة التركيز:" to Pair("اختر التطبيقات المُراد حظرها أثناء جلسة التركيز:", "Select apps to block during focus session:"),
        "مدة الجلسة (بالدقائق):" to Pair("مدة الجلسة (بالدقائق):", "Session Duration (Minutes):"),
        "دقيقة" to Pair("دقيقة", "minute(s)"),
        "بدء جلسة التركيز الآن 🔒" to Pair("بدء جلسة التركيز الآن 🔒", "Start Focus Session Now 🔒"),
        "جلسة التركيز نشطة حالياً" to Pair("جلسة التركيز نشطة حالياً", "Focus Session is Active Now"),
        "الوقت المتبقي:" to Pair("الوقت المتبقي:", "Remaining Time:"),
        "إنهاء الجلسة 🔓" to Pair("إنهاء الجلسة 🔓", "End Session 🔓"),
        "تنبيه هام!" to Pair("تنبيه هام!", "Important Notice!"),
        "تطبيقات قيد الحظر" to Pair("تطبيقات قيد الحظر", "Blocked Apps"),
        "تطبيق نشط حالياً" to Pair("تطبيق نشط حالياً", "App is active now"),
        
        // Quran Screen
        "المصحف والذكر" to Pair("المصحف والذكر", "Quran & Prayers"),
        "ورد التلاوة اليومي" to Pair("ورد التلاوة اليومي", "Daily Quran Reading"),
        "تعديل الورد اليومي" to Pair("تعديل الورد اليومي", "Edit Daily Reading"),
        "أذكار اليومية" to Pair("أذكار اليومية", "Daily Azkar"),
        "أذكار الصباح ☀️" to Pair("أذكار الصباح ☀️", "Morning Azkar ☀️"),
        "أذكار المساء 🌅" to Pair("أذكار المساء 🌅", "Evening Azkar 🌅"),
        "التسبيح الإلكتروني" to Pair("التسبيح الإلكتروني", "Electronic Tasbih"),
        "مسائل فقهية يومية" to Pair("مسائل فقهية يومية", "Daily Fiqh Q&A"),
        "إعدادات التنبيهات والأذكار على الشاشة" to Pair("إعدادات التنبيهات والأذكار على الشاشة", "On-Screen Azkar Overlay Settings"),
        
        // AI Coach Screen
        "مستشار الإنتاجية والصحة بذكاء Gemini 🤖" to Pair("مستشار الإنتاجية والصحة بذكاء Gemini 🤖", "Productivity & Health Advisor powered by Gemini 🤖"),
        "اطلب خطط مخصصة، تحليل يومي لعاداتك واستخدام الهاتف، أو استشارات إنتاجية ودينية رفيعة المستوى." to Pair(
            "اطلب خطط مخصصة، تحليل يومي لعاداتك واستخدام الهاتف، أو استشارات إنتاجية ودينية رفيعة المستوى.",
            "Request personalized plans, daily habit & phone usage analysis, or high-level productivity and religious guidance."
        ),
        "تحديث التحليل والمشورة 🔄" to Pair("تحديث التحليل والمشورة 🔄", "Update Analysis & Advice 🔄"),
        "اسأل مستشار جيمي عن أي شيء..." to Pair("اسأل مستشار جيمي عن أي شيء...", "Ask Gemini Advisor anything..."),
        "أرسل السؤال ⚡" to Pair("أرسل السؤال ⚡", "Send Question ⚡"),
        
        // General buttons & UI
        "إضافة" to Pair("إضافة", "Add"),
        "تم" to Pair("تم", "Done"),
        "س" to Pair("س", "h"),
        "د" to Pair("د", "m"),
        "ث" to Pair("ث", "s"),
        "تصفير" to Pair("تصفير", "Reset"),
        "تحديث" to Pair("تحديث", "Refresh"),
        "جُلب حياً عبر واجهة Aladhan المفتوحة 📡" to Pair("جُلب حياً عبر واجهة Aladhan المفتوحة 📡", "Fetched live via Aladhan Open API 📡"),
        "حساب فلكي محلي عالي الدقة 🌙" to Pair("حساب فلكي محلي عالي الدقة 🌙", "High accuracy local astronomical calc 🌙"),
        
        // Heatmap Translation
        "التقويم الشهري (Heatmap) 📅" to Pair("التقويم الشهري (Heatmap) 📅", "Monthly Calendar (Heatmap) 📅"),
        "الرسم الأسبوعي 📊" to Pair("الرسم الأسبوعي 📊", "Weekly Chart 📊"),
        "كثافة الالتزام:  " to Pair("كثافة الالتزام:  ", "Commitment Level:  "),
        "أح" to Pair("أح", "Su"),
        "ن" to Pair("ن", "Mo"),
        "ث" to Pair("ث", "Tu"),
        "ر" to Pair("ر", "We"),
        "خ" to Pair("خ", "Th"),
        "ج" to Pair("ج", "Fr"),
        "س" to Pair("س", "Sa"),
        "يناير" to Pair("يناير", "January"),
        "فبراير" to Pair("فبراير", "February"),
        "مارس" to Pair("مارس", "March"),
        "أبريل" to Pair("أبريل", "April"),
        "مايو" to Pair("مايو", "May"),
        "يونيو" to Pair("يونيو", "June"),
        "يوليو" to Pair("يوليو", "July"),
        "أغسطس" to Pair("أغسطس", "August"),
        "سبتمبر" to Pair("سبتمبر", "September"),
        "أكتوبر" to Pair("أكتوبر", "October"),
        "نوفمبر" to Pair("نوفمبر", "November"),
        "ديسمبر" to Pair("ديسمبر", "December"),
        
        // Phone Usage
        "إجمالي الوقت: ٣ ساعات و ٤٥ دقيقة" to Pair("إجمالي الوقت: ٣ ساعات و ٤٥ دقيقة", "Total time: 3 hours and 45 minutes"),
        "لم يتم رصد استخدام للهاتف اليوم أو لم يتم منح الصلاحية 📊" to Pair(
            "لم يتم رصد استخدام للهاتف اليوم أو لم يتم منح الصلاحية 📊",
            "No phone usage detected today or permission not granted 📊"
        ),
        "طلب تفعيل صلاحية رصد الاستخدام" to Pair("طلب تفعيل صلاحية رصد الاستخدام", "Enable Usage Stats Permission"),
        "إجمالي وقت الشاشة اليوم:" to Pair("إجمالي وقت الشاشة اليوم:", "Total screen time today:"),
        "التقارير اليومية للأجهزة الذكية" to Pair("التقارير اليومية للأجهزة الذكية", "Smart Device Daily Reports"),
        
        // Additional Home Screen translations
        "الصلاة القادمة" to Pair("الصلاة القادمة", "Next Prayer"),
        "استخدام الهاتف" to Pair("استخدام الهاتف", "Phone Usage"),
        "الورد اليومي: ٧٥٪" to Pair("الورد اليومي: ٧٥٪", "Daily Quran: 75%"),
        "بعد قليل" to Pair("بعد قليل", "Shortly"),
        "خلال دافئة" to Pair("خلال دافئة", "Soon"),
        "+١٢٪ عن الأمس" to Pair("+١٢٪ عن الأمس", "+12% vs Yesterday"),
        "بدء جلسة تركيز (٢٥ د)" to Pair("بدء جلسة تركيز (٢٥ د)", "Start Focus (25m)"),
        "ذكاء اصطناعي • نشط ✨" to Pair("ذكاء اصطناعي • نشط ✨", "AI • Active ✨"),
        "وقت ذروة الإنتاجية المكتشف: الآن" to Pair("وقت ذروة الإنتاجية المكتشف: الآن", "Productivity Peak: NOW"),
        "بناءً على نشاطك، هذا هو أفضل وقت لإتمام مهام البرمجة والعبادة وعاداتك اليومية. تم قفل التطبيقات المشتتة." to Pair(
            "بناءً على نشاطك، هذا هو أفضل وقت لإتمام مهام البرمجة والعبادة وعاداتك اليومية. تم قفل التطبيقات المشتتة.",
            "Based on your habits, this is the optimal window for dev & spiritual duties. Screen lock activated."
        )
    )
}

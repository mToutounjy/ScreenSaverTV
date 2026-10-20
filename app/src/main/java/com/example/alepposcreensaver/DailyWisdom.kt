package com.example.alepposcreensaver

import java.time.LocalDate
import kotlin.random.Random

data class DailyHadithEntry(val text: String, val bukhariNumber: Int)

object DailyHadith {
    private val hadiths = listOf(
        DailyHadithEntry("إنما الأعمال بالنيات", 1),
        DailyHadithEntry("بني الإسلام على خمس", 8),
        DailyHadithEntry("الإيمان بضع وستون شعبة، والحياء شعبة من الإيمان", 9),
        DailyHadithEntry("المسلم من سلم المسلمون من لسانه ويده", 10),
        DailyHadithEntry("تطعم الطعام وتقرأ السلام على من عرفت ومن لم تعرف", 12),
        DailyHadithEntry("لا يؤمن أحدكم حتى يحب لأخيه ما يحب لنفسه", 13),
        DailyHadithEntry("لا يؤمن أحدكم حتى أكون أحب إليه من والده وولده والناس أجمعين", 15),
        DailyHadithEntry("آية المنافق ثلاث: إذا حدث كذب، وإذا وعد أخلف، وإذا اؤتمن خان", 33),
        DailyHadithEntry("يسروا ولا تعسروا، وبشروا ولا تنفروا", 69),
        DailyHadithEntry("من صلى البردين دخل الجنة", 574),
        DailyHadithEntry("خيركم من تعلم القرآن وعلمه", 5027),
        DailyHadithEntry("من لا يرحم لا يرحم", 6013),
        DailyHadithEntry("لا تغضب", 6116),
        DailyHadithEntry("نعمتان مغبون فيهما كثير من الناس: الصحة والفراغ", 6412),
        DailyHadithEntry("ليس الشديد بالصرعة، إنما الشديد الذي يملك نفسه عند الغضب", 6114),
        DailyHadithEntry("إن الله يحب الرفق في الأمر كله", 6024),
        DailyHadithEntry("إن خياركم أحاسنكم أخلاقا", 6035),
        DailyHadithEntry("سباب المسلم فسوق، وقتاله كفر", 6044),
        DailyHadithEntry("لا يدخل الجنة قتات", 6056),
        DailyHadithEntry("اللهم حوالينا ولا علينا", 6093),
        DailyHadithEntry("كل معروف صدقة", 6021),
        DailyHadithEntry("اتقوا النار ولو بشق تمرة، فإن لم تجد فبكلمة طيبة", 6023),
        DailyHadithEntry("أنا وكافل اليتيم في الجنة هكذا", 6005),
        DailyHadithEntry("في كل ذات كبد رطبة أجر", 6009),
        DailyHadithEntry("ما زال يوصيني جبريل بالجار حتى ظننت أنه سيورثه", 6014),
        DailyHadithEntry("الكلمة الطيبة صدقة", 2989),
        DailyHadithEntry("اليد العليا خير من اليد السفلى", 1429),
        DailyHadithEntry("العمرة إلى العمرة كفارة لما بينهما", 1773),
        DailyHadithEntry("من قام رمضان إيمانا واحتسابا غفر له ما تقدم من ذنبه", 2009),
        DailyHadithEntry("كل مسكر حرام", 6124)
    )

    fun forDate(date: LocalDate): DailyHadithEntry =
        hadiths[Random(date.toEpochDay()).nextInt(hadiths.size)]
}

package com.example.signlearn

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class Badge(val id: String, val emoji: String, val title: String, val desc: String)

/**
 * Everything the app remembers (saved on the phone with SharedPreferences):
 * profile, XP, level, streak, hearts, finished lessons and badges.
 */
object ProgressStore {

    const val MAX_HEARTS = 5
    private const val HEART_REFILL_MS = 20 * 60 * 1000L   // +1 heart every 20 minutes
    const val SIGNS_PER_HEART = 5                         // 5 camera signs = +1 heart

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences("signlearn_v2", Context.MODE_PRIVATE)
        }
    }

    // ------------------------------------------------------------------ profile
    val hasProfile: Boolean get() = prefs.getString("name", null) != null
    var name: String
        get() = prefs.getString("name", "Learner") ?: "Learner"
        set(v) = prefs.edit().putString("name", v).apply()
    var dailyGoal: Int
        get() = prefs.getInt("dailyGoal", 30)
        set(v) = prefs.edit().putInt("dailyGoal", v).apply()
    var soundOn: Boolean
        get() = if (::prefs.isInitialized) prefs.getBoolean("sound", true) else true
        set(v) = prefs.edit().putBoolean("sound", v).apply()
    var reminderOn: Boolean
        get() = prefs.getBoolean("reminderOn", true)
        set(v) = prefs.edit().putBoolean("reminderOn", v).apply()
    var reminderHour: Int
        get() = prefs.getInt("reminderHour", 19)
        set(v) = prefs.edit().putInt("reminderHour", v).apply()
    var reminderMinute: Int
        get() = prefs.getInt("reminderMinute", 0)
        set(v) = prefs.edit().putInt("reminderMinute", v).apply()

    // ------------------------------------------------------------------ days
    /** Day number in the phone's local time zone (works on every Android version). */
    fun today(): Long {
        val c = Calendar.getInstance()
        val local = c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)
        return local / 86_400_000L
    }

    // ------------------------------------------------------------------ XP + level
    val xp: Int get() = prefs.getInt("xp", 0)

    val xpToday: Int
        get() = if (prefs.getLong("xpDay", -1) == today()) prefs.getInt("xpToday", 0) else 0

    /** Adds XP and returns true if the learner levelled up. */
    fun addXp(amount: Int): Boolean {
        val before = level
        val t = today()
        val todayXp = xpToday + amount
        prefs.edit()
            .putInt("xp", xp + amount)
            .putInt("xpToday", todayXp)
            .putLong("xpDay", t)
            .apply()
        return level > before
    }

    // Level n starts at 25·n·(n−1) XP → 0, 50, 150, 300, 500, 750 ...
    private fun levelForXp(x: Int): Int = floor((1 + sqrt(1 + 4.0 * x / 25.0)) / 2.0).toInt()
    fun xpForLevel(level: Int): Int = 25 * level * (level - 1)
    val level: Int get() = levelForXp(xp)

    /** 0..1 progress inside the current level. */
    val levelProgress: Float
        get() {
            val start = xpForLevel(level)
            val end = xpForLevel(level + 1)
            return (xp - start).toFloat() / (end - start).toFloat()
        }

    // ------------------------------------------------------------------ streak
    /** Streak still alive? (practiced today or yesterday) */
    val streak: Int
        get() {
            val last = prefs.getLong("lastDay", -10)
            return if (last >= today() - 1) prefs.getInt("streak", 0) else 0
        }
    val bestStreak: Int get() = prefs.getInt("bestStreak", 0)
    val practicedToday: Boolean get() = prefs.getLong("lastDay", -10) == today()

    /** Call whenever the learner finishes something. Returns true if the streak grew today. */
    fun recordActivity(): Boolean {
        val t = today()
        val last = prefs.getLong("lastDay", -10)
        if (last == t) return false
        val newStreak = if (last == t - 1) prefs.getInt("streak", 0) + 1 else 1
        prefs.edit()
            .putInt("streak", newStreak)
            .putInt("bestStreak", max(bestStreak, newStreak))
            .putLong("lastDay", t)
            .apply()
        return true
    }

    // ------------------------------------------------------------------ hearts
    val hearts: Int
        get() {
            var h = prefs.getInt("hearts", MAX_HEARTS)
            if (h >= MAX_HEARTS) return MAX_HEARTS
            val updated = prefs.getLong("heartsAt", System.currentTimeMillis())
            val gained = ((System.currentTimeMillis() - updated) / HEART_REFILL_MS).toInt()
            if (gained > 0) {
                h = min(MAX_HEARTS, h + gained)
                prefs.edit()
                    .putInt("hearts", h)
                    .putLong("heartsAt", updated + gained * HEART_REFILL_MS)
                    .apply()
            }
            return h
        }

    /** Minutes until the next free heart (0 if full). */
    val minutesToNextHeart: Int
        get() {
            if (hearts >= MAX_HEARTS) return 0
            val updated = prefs.getLong("heartsAt", System.currentTimeMillis())
            val left = HEART_REFILL_MS - (System.currentTimeMillis() - updated)
            return max(1, (left / 60_000L).toInt() + 1)
        }

    fun loseHeart() {
        val h = hearts
        if (h <= 0) return
        val e = prefs.edit().putInt("hearts", h - 1)
        if (h == MAX_HEARTS) e.putLong("heartsAt", System.currentTimeMillis())
        e.apply()
    }

    fun addHeart() {
        val h = hearts
        if (h < MAX_HEARTS) prefs.edit().putInt("hearts", h + 1).apply()
    }

    fun refillHearts() = prefs.edit().putInt("hearts", MAX_HEARTS).apply()

    // ------------------------------------------------------------------ lessons
    private val completed: Set<String> get() = prefs.getStringSet("completed", emptySet()) ?: emptySet()
    fun isCompleted(lessonId: String) = lessonId in completed
    val lessonsDone: Int get() = prefs.getInt("lessonsDone", 0)
    val perfectLessons: Int get() = prefs.getInt("perfect", 0)

    /** A lesson is open if it's the first one or the one before it is finished. */
    fun isUnlocked(lessonId: String): Boolean {
        val all = Course.allLessons
        val i = all.indexOfFirst { it.id == lessonId }
        return i <= 0 || isCompleted(all[i - 1].id)
    }

    /** The first lesson that isn't finished yet (the one to "START"). */
    val currentLesson: Lesson?
        get() = Course.allLessons.firstOrNull { !isCompleted(it.id) }

    fun completeLesson(lessonId: String, perfect: Boolean) {
        val set = HashSet(completed)
        set.add(lessonId)
        prefs.edit()
            .putStringSet("completed", set)
            .putInt("lessonsDone", lessonsDone + 1)
            .putInt("perfect", perfectLessons + if (perfect) 1 else 0)
            .apply()
    }

    // ------------------------------------------------------------------ signs
    val learnedSigns: Set<String> get() = prefs.getStringSet("learned", emptySet()) ?: emptySet()
    fun markLearned(key: String) {
        val set = HashSet(learnedSigns)
        if (set.add(key)) prefs.edit().putStringSet("learned", set).apply()
    }

    val cameraSigns: Int get() = prefs.getInt("cameraSigns", 0)

    /** Records one correct camera sign. Returns true if it earned a heart. */
    fun addCameraSign(): Boolean {
        val n = cameraSigns + 1
        prefs.edit().putInt("cameraSigns", n).apply()
        if (n % SIGNS_PER_HEART == 0 && hearts < MAX_HEARTS) {
            addHeart()
            return true
        }
        return false
    }

    val speedBest: Int get() = prefs.getInt("speedBest", 0)
    /** Saves the score; returns true if it's a new best. */
    fun saveSpeedScore(score: Int): Boolean {
        if (score > speedBest) {
            prefs.edit().putInt("speedBest", score).apply()
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ badges
    val allBadges = listOf(
        Badge("first", "🎉", "First Steps", "Finish your first lesson"),
        Badge("perfect", "🎯", "Sharp Eye", "Finish a lesson with no mistakes"),
        Badge("cam1", "📷", "On Camera", "Sign your first letter to the camera"),
        Badge("cam25", "🙌", "Busy Hands", "Sign 25 letters to the camera"),
        Badge("streak3", "🔥", "On Fire", "Reach a 3-day streak"),
        Badge("streak7", "⚡", "Unstoppable", "Reach a 7-day streak"),
        Badge("xp100", "⭐", "Rising Star", "Earn 100 XP"),
        Badge("xp500", "🌟", "Superstar", "Earn 500 XP"),
        Badge("speed10", "⏱️", "Speedy Signer", "Score 10+ in a Speed Round"),
        Badge("alphabet", "🔤", "Alphabet Pro", "Finish Units 1–3 (A–Z)"),
        Badge("polite", "🙏", "So Polite", "Finish Unit 4: Greetings"),
        Badge("grad", "🏆", "Graduate", "Finish every lesson")
    )

    val unlockedBadges: Set<String> get() = prefs.getStringSet("badges", emptySet()) ?: emptySet()

    private fun unitDone(n: Int) = Course.units.first { it.number == n }.lessons.all { isCompleted(it.id) }

    /** Checks every badge and returns the ones that were JUST unlocked. */
    fun checkBadges(): List<Badge> {
        val have = HashSet(unlockedBadges)
        val earned = allBadges.filter { b ->
            b.id !in have && when (b.id) {
                "first" -> lessonsDone >= 1
                "perfect" -> perfectLessons >= 1
                "cam1" -> cameraSigns >= 1
                "cam25" -> cameraSigns >= 25
                "streak3" -> bestStreak >= 3
                "streak7" -> bestStreak >= 7
                "xp100" -> xp >= 100
                "xp500" -> xp >= 500
                "speed10" -> speedBest >= 10
                "alphabet" -> unitDone(1) && unitDone(2) && unitDone(3)
                "polite" -> unitDone(4)
                "grad" -> Course.allLessons.all { isCompleted(it.id) }
                else -> false
            }
        }
        if (earned.isNotEmpty()) {
            have.addAll(earned.map { it.id })
            prefs.edit().putStringSet("badges", have).apply()
        }
        return earned
    }

    fun badge(id: String): Badge? = allBadges.firstOrNull { it.id == id }

    /** Wipes progress but keeps the name and settings. */
    fun resetProgress() {
        val keep = name
        val goal = dailyGoal
        val sound = soundOn
        prefs.edit().clear().apply()
        name = keep
        dailyGoal = goal
        soundOn = sound
    }
}

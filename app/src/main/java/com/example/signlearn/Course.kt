package com.example.signlearn

enum class SignKind { LETTER, WORD }

/** One sign the app can teach. [key] matches the drawing in HandShapes. */
data class Sign(
    val key: String,
    val label: String,
    val kind: SignKind,
    val how: String,
    val tip: String,
    val emoji: String = ""
) {
    /** True if the camera can check this sign. */
    val cameraReady: Boolean get() = key in SignClassifier.SUPPORTED
}

enum class LessonType { LEARN, CAMERA }

data class Lesson(
    val id: String,
    val title: String,
    val icon: String,
    val type: LessonType,
    val signKeys: List<String>,
    /** For camera lessons that spell a word (e.g. "LAB"). */
    val word: String? = null
)

data class CourseUnit(
    val number: Int,
    val title: String,
    val subtitle: String,
    val color: Int,      // R.color.*
    val nodeBg: Int,     // R.drawable.node_*
    val buttonBg: Int,   // R.drawable.btn_*
    val lessons: List<Lesson>
)

/** All course content lives here — edit this file to add more signs or lessons. */
object Course {

    private fun L(k: String, how: String, tip: String) = Sign(k, k, SignKind.LETTER, how, tip)
    private fun W(k: String, label: String, emoji: String, how: String, tip: String) =
        Sign(k, label, SignKind.WORD, how, tip, emoji)

    val signs: Map<String, Sign> = listOf(
        L("A", "Make a fist with your thumb resting straight up against the side of your index finger.", "Think: A is a rock-solid fist."),
        L("B", "Hold your four fingers straight up and together. Fold your thumb across your palm.", "B looks like a flat Book cover."),
        L("C", "Curve your fingers and thumb into the shape of the letter C, like holding a cup.", "Look at your hand from the side — it's a C!"),
        L("D", "Point your index finger up. Touch your other fingertips to your thumb to make a circle.", "Index up + circle = a lowercase d."),
        L("E", "Bend all four fingertips down so they rest on your thumb, which is tucked under them.", "Looks like a little claw."),
        L("F", "Touch your index fingertip to your thumb (an OK circle) and keep the other three fingers up.", "F is the 'OK' hand."),
        L("G", "Point your index finger sideways with your thumb parallel above it.", "Like pinching a tiny gap."),
        L("H", "Point your index and middle fingers sideways, held together.", "H is G with one extra finger."),
        L("I", "Make a fist and raise only your pinky finger.", "Pinky up — I'm fancy!"),
        L("J", "Make an I, then draw a J in the air with your pinky.", "J = I + a hook movement."),
        L("K", "Raise your index and middle fingers in a V and put your thumb between them.", "A V with the thumb peeking in."),
        L("L", "Point your index finger up and stick your thumb out to the side.", "Your hand makes a real L shape."),
        L("M", "Make a fist and tuck your thumb under your first three fingers.", "M has 3 bumps — 3 fingers over the thumb."),
        L("N", "Make a fist and tuck your thumb under your first two fingers.", "N has 2 bumps — 2 fingers over the thumb."),
        L("O", "Curve all your fingers so they touch your thumb, making an O.", "Like peeking through a tube."),
        L("P", "Make a K, then point it downward.", "P is a K that points to the floor."),
        L("Q", "Make a G, then point it downward.", "Q is a G that points to the floor."),
        L("R", "Cross your middle finger over your index finger.", "Fingers crossed for good luck = R."),
        L("S", "Make a fist with your thumb wrapped across the front of your fingers.", "S is a strong, solid fist."),
        L("T", "Make a fist and tuck your thumb between your index and middle fingers.", "The thumb peeks out like a tiny T."),
        L("U", "Raise your index and middle fingers straight up, pressed together.", "Two fingers glued together = U."),
        L("V", "Raise your index and middle fingers spread apart.", "The peace / victory sign."),
        L("W", "Raise your index, middle and ring fingers spread apart. Your thumb holds down the pinky.", "Three fingers make the points of a W."),
        L("X", "Make a fist and raise your index finger bent like a hook.", "A pirate's hook."),
        L("Y", "Stick out your thumb and pinky. Fold the other three fingers.", "The 'hang loose' / 'shaka' sign."),
        L("Z", "Point your index finger and draw a Z in the air.", "Trace a big Z like Zorro."),
        W("HELLO", "Hello", "👋", "Hold a flat hand at your forehead and move it outward, like a salute.", "Smile while you sign it!"),
        W("THANK_YOU", "Thank you", "🙏", "Touch the fingertips of a flat hand to your chin, then move it forward toward the person.", "Like blowing a thank-you toward someone."),
        W("PLEASE", "Please", "🥺", "Rub a flat hand in a circle on your chest.", "Gentle circles, like a polite request."),
        W("SORRY", "Sorry", "😔", "Rub a fist (A hand) in a circle on your chest.", "Same as Please, but with a fist."),
        W("YES", "Yes", "✅", "Make a fist (S hand) and bob it up and down, like a head nodding.", "Your fist is a nodding head."),
        W("NO", "No", "❌", "Snap your index and middle fingers down onto your thumb.", "Like a little mouth closing: no!"),
        W("HELP", "Help", "🆘", "Put a thumbs-up fist on your other flat palm and lift both hands up together.", "One hand lifting the other up."),
        W("GOOD", "Good", "👍", "Touch flat fingers to your chin, then bring the hand down onto your other palm.", "Start at the mouth — something tasted good!"),
        W("ILY", "I love you", "🤟", "Raise your thumb, index finger and pinky at the same time.", "It combines the letters I, L and Y!"),
        W("EAT", "Eat", "🍚", "Bunch your fingertips together and tap them to your mouth.", "Like putting food in your mouth."),
        W("DRINK", "Drink", "🥤", "Make a C hand like holding a cup and tip it toward your mouth.", "Pretend you're sipping from a glass."),
        W("WATER", "Water", "💧", "Make a W and tap your index finger to your chin twice.", "W for Water, at the mouth."),
        W("FRIEND", "Friend", "🤝", "Hook your index fingers together, then switch which one is on top.", "Two friends linked together.")
    ).associateBy { it.key }

    val units: List<CourseUnit> = listOf(
        CourseUnit(
            1, "Unit 1 · Alphabet A–I", "Your first fingerspelling letters",
            R.color.primary, R.drawable.node_primary, R.drawable.btn_primary,
            listOf(
                Lesson("u1l1", "Letters A–D", "🔤", LessonType.LEARN, listOf("A", "B", "C", "D")),
                Lesson("u1l2", "Letters E–I", "✋", LessonType.LEARN, listOf("E", "F", "G", "H", "I")),
                Lesson("u1cam", "Camera check", "📷", LessonType.CAMERA, listOf("A", "B", "D", "F", "I"))
            )
        ),
        CourseUnit(
            2, "Unit 2 · Alphabet J–R", "Movement letters and tricky thumbs",
            R.color.green, R.drawable.node_green, R.drawable.btn_green,
            listOf(
                Lesson("u2l1", "Letters J–M", "🔤", LessonType.LEARN, listOf("J", "K", "L", "M")),
                Lesson("u2l2", "Letters N–R", "👌", LessonType.LEARN, listOf("N", "O", "P", "Q", "R")),
                Lesson("u2cam", "Spell: LAB", "📷", LessonType.CAMERA, listOf("L", "A", "B"), word = "LAB")
            )
        ),
        CourseUnit(
            3, "Unit 3 · Alphabet S–Z", "Finish the whole alphabet",
            R.color.orange, R.drawable.node_orange, R.drawable.btn_orange,
            listOf(
                Lesson("u3l1", "Letters S–V", "🔤", LessonType.LEARN, listOf("S", "T", "U", "V")),
                Lesson("u3l2", "Letters W–Z", "✌️", LessonType.LEARN, listOf("W", "X", "Y", "Z")),
                Lesson("u3cam", "Camera check", "📷", LessonType.CAMERA, listOf("U", "V", "W", "Y"))
            )
        ),
        CourseUnit(
            4, "Unit 4 · Greetings", "Be polite in sign language",
            R.color.blue, R.drawable.node_blue, R.drawable.btn_blue,
            listOf(
                Lesson("u4l1", "Polite words", "👋", LessonType.LEARN, listOf("HELLO", "THANK_YOU", "PLEASE", "SORRY")),
                Lesson("u4l2", "Quick answers", "💬", LessonType.LEARN, listOf("YES", "NO", "HELP", "GOOD")),
                Lesson("u4cam", "Sign: I love you", "📷", LessonType.CAMERA, listOf("I", "L", "Y", "ILY"))
            )
        ),
        CourseUnit(
            5, "Unit 5 · Everyday", "Words you'll use every day",
            R.color.pink, R.drawable.node_pink, R.drawable.btn_red,
            listOf(
                Lesson("u5l1", "Daily life", "🍚", LessonType.LEARN, listOf("ILY", "EAT", "DRINK", "WATER", "FRIEND")),
                Lesson("u5cam", "Final: spell BUILD", "🏆", LessonType.CAMERA, listOf("B", "U", "I", "L", "D"), word = "BUILD")
            )
        )
    )

    val allLessons: List<Lesson> get() = units.flatMap { it.lessons }

    fun sign(key: String): Sign = signs.getValue(key)
    fun lesson(id: String): Lesson? = allLessons.firstOrNull { it.id == id }
    fun unitOf(lessonId: String): CourseUnit? = units.firstOrNull { u -> u.lessons.any { it.id == lessonId } }

    val letters: List<Sign> get() = signs.values.filter { it.kind == SignKind.LETTER }
    val words: List<Sign> get() = signs.values.filter { it.kind == SignKind.WORD }

    /** Words the camera can fully check (every letter is supported). */
    val spellWords = listOf("BAD", "DAY", "FLY", "BUY", "LAB", "WILD", "BUILD", "FLUID", "BAWL", "IDLY")
}

package Com.ak.testseries.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import Com.ak.testseries.exam.UniversalExamRegistry

@Entity(tableName = "tests")
data class TestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val institute: String,
    val batchName: String,
    val facultyName: String,
    val language: String, // "English", "Hindi", "Hinglish", "Bilingual"
    val subject: String, // "Physics", "Chemistry", "Biology", "Full Syllabus"
    val difficulty: String, // "Easy", "Medium", "Hard"
    val testType: String, // "Full Syllabus", "Part Test", "Chapter Test", "PYQ"
    val questionsCount: Int,
    val isOfficial: Boolean, // Official Admin badge
    val isVerifiedCommunity: Boolean, // Verified Community badge
    val isPublic: Boolean = true, // Whether test is public or private
    val countsTowardRanking: Boolean = true, // Qualifying test for competitive ranking
    val rating: Float = 4.5f,
    val rating1Count: Int = 0,
    val rating2Count: Int = 0,
    val rating3Count: Int = 0,
    val rating4Count: Int = 0,
    val rating5Count: Int = 0,
    val attemptsCount: Int = 0,
    val downloadsCount: Int = 0,
    val isDownloaded: Boolean = false,
    val isBookmarked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Published", // "Draft", "Under Review", "Published"
    val aiConfidence: Int = 98,
    val uploaderId: String = "anonymous", // e.g. 98%

    // Deep Mapping Extensions
    val targetExam: String = "NEET",
    val durationMinutes: Int = 0,
    val classLevel: String = "All", // "11th", "12th", "Dropper", "All"
    val chapter: String = "All",
    val topic: String = "All",
    val imageUrl: String? = null,
    val questionsJson: String? = null
)

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val testId: String,
    val questionText: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctAnswer: String, // "A", "B", "C", "D"
    val explanation: String,
    val subject: String, // "Physics", "Chemistry", "Biology"
    val chapter: String,
    val topic: String = "",
    val difficulty: String = "Medium",
    val isBookmarked: Boolean = false,
    val questionTextHi: String? = null,
    val optionAHi: String? = null,
    val optionBHi: String? = null,
    val optionCHi: String? = null,
    val optionDHi: String? = null,
    val explanationHi: String? = null,
    val questionType: String = "MCQ", // "MCQ" or "NUMERICAL"
    val section: String = "SECTION_A", // "SECTION_A" or "SECTION_B"
    val needsImage: Boolean = false,
    val imageUrl: String? = null
)

@Entity(
    tableName = "attempts",
    indices = [Index(value = ["userId", "attemptSessionToken"], unique = false)]
)
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String = "", // Partition attempt history by authenticated user
    val testId: String,
    val testTitle: String,
    val score: Int, // e.g. 612 / 720
    val maxScore: Int, // e.g. 720
    val percentile: Double = 0.0,
    val accuracy: Double = 0.0,
    val correctCount: Int,
    val incorrectCount: Int,
    val skippedCount: Int,
    val timeSpentSeconds: Long,
    val dateTaken: Long = System.currentTimeMillis(),
    val answersJson: String, // String-encoded state of user answers
    val attemptSessionToken: String = "", // Stable idempotency key for deduplication
    val verifiedServerSide: Boolean = false, // Server verification guarantee
    val submissionStatus: String = "CONFIRMED" // CONFIRMED, PENDING, OFFLINE
)

data class QuestionReport(
    val reportId: String = "",
    val testId: String = "",
    val testTitle: String = "",
    val questionId: String = "",
    val questionText: String = "",
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = "",
    val correctAnswer: String = "",
    val reporterUserId: String = "",
    val reporterUserEmail: String = "",
    val userNote: String = "",
    val aiAnalysis: String = "",
    val status: String = "PENDING", // PENDING, RESOLVED, DISMISSED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class BookmarkedQuestionRef(
    val questionId: String = "",
    val testId: String = "",
    val testTitle: String = "",
    val subject: String = "",
    val chapter: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_searches")
data class RecentSearchEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ncert_syllabus")
data class SyllabusEntity(
    @PrimaryKey val id: String,
    val classLevel: String, // "Class 11", "Class 12"
    val subject: String, // "Physics", "Chemistry", "Biology"
    val chapter: String,
    val topics: String = "",
    val totalTests: Int = 12
)

object SyllabusData {
    val classes = listOf("11th", "12th", "Dropper")
    val subjects = listOf("Physics", "Chemistry", "Biology", "Mathematics", "Quantitative Aptitude", "Reasoning", "English", "General Awareness", "Child Development & Pedagogy", "Environmental Studies")

    val quantChapters = listOf(
        "Number Systems & Simplification",
        "Percentage & Averages",
        "Ratio, Proportion & Partnership",
        "Profit, Loss & Discount",
        "Simple & Compound Interest",
        "Time, Work & Wages",
        "Time, Speed & Distance (Boats & Trains)",
        "Mensuration 2D & 3D",
        "Algebra & Quadratic Equations",
        "Trigonometry & Heights and Distances",
        "Geometry & Circles",
        "Data Interpretation (Tables & Charts)"
    )

    val reasoningChapters = listOf(
        "Analogies & Classification",
        "Number & Alphabetical Series",
        "Coding & Decoding",
        "Blood Relations",
        "Direction & Distance",
        "Syllogism & Venn Diagrams",
        "Seating Arrangement & Puzzles",
        "Inequality & Input-Output",
        "Statement & Assumptions / Arguments",
        "Non-Verbal & Pattern Completion",
        "Order & Ranking",
        "Mathematical Operations"
    )

    val gaChapters = listOf(
        "Indian History (Ancient, Medieval, Modern)",
        "Indian Polity & Constitution",
        "Geography (Physical, Indian & World)",
        "Indian Economy & Union Budget",
        "General Science (Physics, Chemistry, Biology)",
        "Environmental Science & Ecology",
        "Current National & International Events",
        "Books, Authors, Awards & Honors",
        "Art, Culture & Heritage",
        "Banking & Financial Awareness"
    )

    val englishChapters = listOf(
        "Reading Comprehension",
        "Spotting Errors & Grammar Rules",
        "Fill in the Blanks & Sentence Completion",
        "Cloze Test",
        "Synonyms & Antonyms",
        "Idioms, Phrases & Phrasal Verbs",
        "One Word Substitution & Spellings",
        "Sentence Improvement & Para Jumbles",
        "Active & Passive Voice",
        "Direct & Indirect Speech"
    )

    val cdpChapters = listOf(
        "Concept & Principles of Child Development",
        "Influence of Heredity & Environment",
        "Piaget, Kohlberg and Vygotsky Theories",
        "Child-Centered and Progressive Education",
        "Concepts of Inclusive Education",
        "Learning and Pedagogy Principles",
        "Assessment and Evaluation of Learning"
    )

    val evsChapters = listOf(
        "Family and Friends",
        "Food, Nutrition and Health",
        "Shelter and Habitat",
        "Water and Water Resources",
        "Travel and Transportation",
        "Things We Make and Do",
        "Environmental Pedagogy"
    )

    // Mapping Class -> Subject -> List of Chapters
    val syllabus = mapOf(
        "11th" to mapOf(
            "Physics" to listOf(
                "Physical World and Measurement",
                "Kinematics",
                "Laws of Motion",
                "Work, Energy and Power",
                "Motion of System of Particles and Rigid Body",
                "Gravitation",
                "Properties of Bulk Matter",
                "Thermodynamics",
                "Behaviour of Perfect Gas and Kinetic Theory",
                "Oscillations and Waves"
            ),
            "Chemistry" to listOf(
                "Some Basic Concepts of Chemistry",
                "Structure of Atom",
                "Classification of Elements and Periodicity in Properties",
                "Chemical Bonding and Molecular Structure",
                "Thermodynamics",
                "Equilibrium",
                "Redox Reactions",
                "Organic Chemistry - Some Basic Principles and Techniques",
                "Hydrocarbons"
            ),
            "Biology" to listOf(
                "Diversity in Living World",
                "Structural Organisation in Animals and Plants",
                "Cell Structure and Function",
                "Plant Physiology",
                "Human Physiology"
            ),
            "Mathematics" to listOf(
                "Algebra",
                "Trigonometry",
                "Coordinate Geometry"
            ),
            "Quantitative Aptitude" to quantChapters,
            "Quant" to quantChapters,
            "Reasoning" to reasoningChapters,
            "General Awareness" to gaChapters,
            "English" to englishChapters
        ),
        "12th" to mapOf(
            "Physics" to listOf(
                "Electrostatics",
                "Current Electricity",
                "Magnetic Effects of Current and Magnetism",
                "Electromagnetic Induction and Alternating Currents",
                "Electromagnetic Waves",
                "Optics",
                "Dual Nature of Matter and Radiation",
                "Atoms and Nuclei",
                "Electronic Devices",
                "Experimental Skills"
            ),
            "Chemistry" to listOf(
                "Solutions",
                "Electrochemistry",
                "Chemical Kinetics",
                "d and f Block Elements",
                "Coordination Compounds",
                "Haloalkanes and Haloarenes",
                "Alcohols, Phenols and Ethers",
                "Aldehydes, Ketones and Carboxylic Acids",
                "Amines",
                "Biomolecules",
                "Principles Related to Practical Chemistry"
            ),
            "Biology" to listOf(
                "Reproduction",
                "Genetics and Evolution",
                "Biology and Human Welfare",
                "Biotechnology and Its Applications",
                "Ecology and Environment"
            ),
            "Mathematics" to listOf(
                "Calculus",
                "Vectors & 3D Geometry"
            ),
            "Quantitative Aptitude" to quantChapters,
            "Quant" to quantChapters,
            "Reasoning" to reasoningChapters,
            "General Awareness" to gaChapters,
            "English" to englishChapters
        ),
        "Dropper" to mapOf(
            "Physics" to listOf(
                "Physical World and Measurement",
                "Kinematics",
                "Laws of Motion",
                "Work, Energy and Power",
                "Motion of System of Particles and Rigid Body",
                "Gravitation",
                "Properties of Bulk Matter",
                "Thermodynamics",
                "Behaviour of Perfect Gas and Kinetic Theory",
                "Oscillations and Waves",
                "Electrostatics",
                "Current Electricity",
                "Magnetic Effects of Current and Magnetism",
                "Electromagnetic Induction and Alternating Currents",
                "Electromagnetic Waves",
                "Optics",
                "Dual Nature of Matter and Radiation",
                "Atoms and Nuclei",
                "Electronic Devices",
                "Experimental Skills"
            ),
            "Chemistry" to listOf(
                "Some Basic Concepts of Chemistry",
                "Structure of Atom",
                "Classification of Elements and Periodicity in Properties",
                "Chemical Bonding and Molecular Structure",
                "Thermodynamics",
                "Equilibrium",
                "Redox Reactions",
                "Organic Chemistry - Some Basic Principles and Techniques",
                "Hydrocarbons",
                "Solutions",
                "Electrochemistry",
                "Chemical Kinetics",
                "d and f Block Elements",
                "Coordination Compounds",
                "Haloalkanes and Haloarenes",
                "Alcohols, Phenols and Ethers",
                "Aldehydes, Ketones and Carboxylic Acids",
                "Amines",
                "Biomolecules",
                "Principles Related to Practical Chemistry"
            ),
            "Biology" to listOf(
                "Diversity in Living World",
                "Structural Organisation in Animals and Plants",
                "Cell Structure and Function",
                "Plant Physiology",
                "Human Physiology",
                "Reproduction",
                "Genetics and Evolution",
                "Biology and Human Welfare",
                "Biotechnology and Its Applications",
                "Ecology and Environment"
            ),
            "Mathematics" to listOf(
                "Algebra",
                "Calculus",
                "Coordinate Geometry",
                "Trigonometry",
                "Vectors & 3D Geometry"
            ),
            "Quantitative Aptitude" to quantChapters,
            "Quant" to quantChapters,
            "Reasoning" to reasoningChapters,
            "General Awareness" to gaChapters,
            "English" to englishChapters
        )
    )

    fun getSubjectsForExam(targetExam: String): List<String> {
        val regExam = UniversalExamRegistry.findExamByNameOrShortName(targetExam)
        if (regExam != null) {
            val subs = regExam.stages.flatMap { it.papers.flatMap { p -> p.subjects.map { s -> s.subjectName } } }.distinct()
            if (subs.isNotEmpty()) return subs
        }

        val upper = targetExam.uppercase()
        return when {
            upper.contains("UPSC") || upper.contains("CIVIL") -> listOf("Indian Polity & Governance", "Economic & Social Development", "History of India & Art & Culture", "Geography & Environment Ecology", "General Science & Technology")
            upper.contains("GATE") -> listOf("General Aptitude", "Engineering Mathematics & Technical Core")
            upper.contains("NEET") -> listOf("Physics", "Chemistry", "Biology")
            upper.contains("JEE") -> listOf("Physics", "Chemistry", "Mathematics")
            upper.contains("CGL") || upper.contains("CHSL") -> listOf("General Intelligence & Reasoning", "General Awareness", "Quantitative Aptitude", "English Comprehension", "English")
            upper.contains("CPO") -> listOf("General Intelligence & Reasoning", "General Knowledge & General Awareness", "Quantitative Aptitude", "English Comprehension")
            upper.contains("TECHNICIAN") || upper.contains("TECH") -> listOf("Mathematics", "General Intelligence & Reasoning", "General Science", "General Awareness")
            upper.contains("MTS") -> listOf("Numerical & Mathematical Ability", "Reasoning Ability & Problem Solving", "General Awareness", "English Language & Comprehension")
            upper.contains("GD") -> listOf("General Intelligence & Reasoning", "General Knowledge & General Awareness", "Elementary Mathematics", "English / Hindi")
            upper.contains("SSC") -> listOf("Reasoning", "Quantitative Aptitude", "General Awareness", "English")
            upper.contains("SBI PO") || upper.contains("IBPS PO") -> listOf("Quantitative Aptitude", "Reasoning Ability", "English Language", "General Awareness")
            upper.contains("SBI CLERK") || upper.contains("IBPS CLERK") -> listOf("Numerical Ability", "Reasoning Ability", "English Language", "General Awareness")
            upper.contains("BANK") || upper.contains("IBPS") || upper.contains("SBI") -> listOf("Reasoning Ability", "Quantitative Aptitude", "English Language", "General Awareness")
            upper.contains("NTPC") -> listOf("Mathematics", "General Intelligence & Reasoning", "General Awareness")
            upper.contains("GROUP D") || upper.contains("LEVEL 1") || upper.contains("LEVEL-1") -> listOf("General Science", "Mathematics", "General Intelligence & Reasoning", "General Awareness")
            upper.contains("ALP") -> listOf("Mathematics", "General Intelligence & Reasoning", "General Science", "General Awareness")
            upper.contains("JE") -> listOf("Mathematics", "General Intelligence & Reasoning", "General Awareness", "General Science")
            upper.contains("RRB") || upper.contains("RAILWAY") -> listOf("Mathematics", "General Intelligence & Reasoning", "General Science", "General Awareness")
            upper.contains("CTET") || upper.contains("TET") || upper.contains("TEACH") -> listOf("Child Development & Pedagogy", "Mathematics", "Environmental Studies", "English")
            upper.contains("CUET") -> listOf("Physics", "Chemistry", "Mathematics", "Biology", "General Awareness", "English")
            upper.contains("CAT") -> listOf("Verbal Ability & Reading Comprehension", "Data Interpretation & Logical Reasoning", "Quantitative Ability")
            upper.contains("NDA") || upper.contains("CDS") || upper.contains("DEFEN") -> listOf("Mathematics", "English", "General Awareness")
            upper.contains("UGC") || upper.contains("NET") -> listOf("Teaching & Research Aptitude", "Reasoning", "General Awareness")
            else -> listOf("Physics", "Chemistry", "Biology", "Mathematics")
        }
    }

    fun getSyllabusForExam(targetExam: String, classLevel: String = "12th"): Map<String, List<String>> {
        val catalogDetails = Com.ak.testseries.exam.SyllabusCatalog.getChapterDetailsForExam(targetExam)
        if (catalogDetails.isNotEmpty()) {
            val normalizedLevel = if (classLevel.contains("11")) "Class 11" else "Class 12"
            val filtered = catalogDetails.filter {
                it.classLevel == normalizedLevel || it.classLevel == "General" || it.classLevel == "Class 11/12" || classLevel == "Dropper" || classLevel == "All"
            }
            if (filtered.isNotEmpty()) {
                val map = mutableMapOf<String, MutableList<String>>()
                filtered.forEach { ch ->
                    map.getOrPut(ch.subject) { mutableListOf() }.add(ch.chapter)
                }
                return map
            }
        }

        val upper = targetExam.uppercase()
        val classMap = syllabus[classLevel] ?: syllabus["12th"] ?: emptyMap()
        val examSubjects = getSubjectsForExam(targetExam)

        val result = mutableMapOf<String, List<String>>()
        for (subj in examSubjects) {
            when (subj) {
                "Physics" -> result["Physics"] = classMap["Physics"] ?: emptyList()
                "Chemistry" -> result["Chemistry"] = classMap["Chemistry"] ?: emptyList()
                "Biology", "Botany", "Zoology" -> result[subj] = classMap["Biology"] ?: emptyList()
                "Mathematics" -> {
                    if (upper.contains("SSC") || upper.contains("RRB") || upper.contains("BANK")) {
                        result[subj] = quantChapters
                    } else {
                        result["Mathematics"] = classMap["Mathematics"] ?: emptyList()
                    }
                }
                "Quantitative Aptitude", "Quant", "Elementary Mathematics", "Numerical & Mathematical Ability", "Numerical Ability" -> result[subj] = quantChapters
                "Reasoning", "Reasoning Ability", "General Intelligence", "General Intelligence & Reasoning", "Reasoning Ability & Problem Solving" -> result[subj] = reasoningChapters
                "General Awareness", "General Knowledge", "General Knowledge & General Awareness" -> result[subj] = gaChapters
                "Indian Polity & Governance", "Economic & Social Development", "History of India & Art & Culture", "Geography & Environment Ecology", "General Science & Technology" -> result[subj] = gaChapters
                "General Aptitude" -> result[subj] = quantChapters
                "Engineering Mathematics & Technical Core" -> result[subj] = listOf(
                    "Linear Algebra & Calculus",
                    "Differential Equations & Complex Variables",
                    "Probability & Statistics",
                    "Numerical Methods & Technical Core"
                )
                "General Science" -> result[subj] = listOf(
                    "Physics (Mechanics, Heat, Light, Electricity)",
                    "Chemistry (Matter, Atoms, Chemical Reactions, Acids & Bases)",
                    "Life Sciences (Cell, Plant & Animal Systems, Human Body, Ecology)"
                )
                "English", "English Language", "English Comprehension", "English Language & Comprehension", "English / Hindi" -> result[subj] = englishChapters
                "Child Development & Pedagogy" -> result[subj] = cdpChapters
                "Environmental Studies" -> result[subj] = evsChapters
                "Verbal Ability & Reading Comprehension", "VARC" -> result[subj] = listOf(
                    "Reading Comprehension Passages",
                    "Para Jumbles & Paragraph Summary",
                    "Para Completion & Sentence Elimination",
                    "Vocabulary & Grammar in Context"
                )
                "Data Interpretation & Logical Reasoning", "DILR" -> result[subj] = listOf(
                    "Tables, Bar Graphs & Pie Charts",
                    "Puzzles & Seating Arrangements",
                    "Binary Logic & Tournaments",
                    "Venn Diagrams & Set Theory",
                    "Matrix Arrangements & Caselets"
                )
                "Quantitative Ability", "QA" -> result[subj] = quantChapters
                "Teaching & Research Aptitude" -> result[subj] = listOf(
                    "Teaching Aptitude & Methods",
                    "Research Aptitude & Ethics",
                    "Comprehension & Communication",
                    "Mathematical Reasoning & Aptitude",
                    "Logical Reasoning & Data Interpretation",
                    "Information & Communication Technology (ICT)",
                    "People, Development & Environment",
                    "Higher Education System"
                )
                else -> result[subj] = classMap[subj] ?: quantChapters
            }
        }
        return result
    }

    fun getTotalChaptersCountForExam(targetExam: String): Int {
        val examSyllabus = getSyllabusForExam(targetExam, "12th")
        return examSyllabus.values.flatten().distinct().size
    }
}

data class StudentData(
    val id: String,
    val name: String,
    val email: String,
    val testsAttempted: String,
    val avgScore: String,
    val isBanned: Boolean
)



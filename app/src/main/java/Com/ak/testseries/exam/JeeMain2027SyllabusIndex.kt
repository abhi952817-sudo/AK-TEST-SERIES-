package Com.ak.testseries.exam

/**
 * Source index for NTA JEE Main 2027 Paper 1.
 *
 * This is deliberately an ingestion index, NOT an approved syllabus snapshot:
 * full official subtopic transcription, Hindi translation review and Admin approval
 * must be completed before this index can be converted into prompt-eligible nodes.
 */
data class SyllabusUnitIndexEntry(
    val examId: String,
    val syllabusVersion: String,
    val stageId: String,
    val paperId: String,
    val subjectId: String,
    val unitId: String,
    val unitNumber: Int,
    val titleEn: String,
    val titleHi: String,
    val sourcePdfPage: Int
)

object JeeMain2027SyllabusIndex {
    const val EXAM_ID = "jee_main"
    const val VERSION = "JEE_MAIN_2027"
    const val STAGE_ID = "jee_paper_1"
    const val PAPER_ID = "jee_main_p1"
    const val SOURCE_ISSUER = "National Testing Agency"
    const val SOURCE_TITLE = "Syllabus for JEE Main Paper 1 (B.E./B.Tech.) - 2027"
    const val SOURCE_URL = "https://cdnbbsr.s3waas.gov.in/s3f8e59f4b2fe7c5705bf878bbd494ccdf/uploads/2026/10/20261024683680768.pdf"
    const val SOURCE_LISTING_URL = "https://jeemain.nta.nic.in/document/syllabus-2027/"
    const val APPROVAL_STATE = "SOURCE_IDENTIFIED_NOT_APPROVED"

    private fun unit(
        subject: String, number: Int, en: String, hi: String, page: Int
    ) = SyllabusUnitIndexEntry(
        examId = EXAM_ID,
        syllabusVersion = VERSION,
        stageId = STAGE_ID,
        paperId = PAPER_ID,
        subjectId = subject,
        unitId = "${subject}_unit_${number.toString().padStart(2, '0')}",
        unitNumber = number,
        titleEn = en,
        titleHi = hi,
        sourcePdfPage = page
    )

    val mathematics = listOf(
        unit("mathematics", 1, "Sets, Relations and Functions", "समुच्चय, संबंध एवं फलन", 1),
        unit("mathematics", 2, "Complex Numbers and Quadratic Equations", "सम्मिश्र संख्याएँ एवं द्विघात समीकरण", 1),
        unit("mathematics", 3, "Matrices and Determinants", "आव्यूह एवं सारणिक", 1),
        unit("mathematics", 4, "Permutations and Combinations", "क्रमचय एवं संचय", 1),
        unit("mathematics", 5, "Binomial Theorem and its Simple Applications", "द्विपद प्रमेय एवं उसके सरल अनुप्रयोग", 1),
        unit("mathematics", 6, "Sequence and Series", "अनुक्रम एवं श्रेणी", 1),
        unit("mathematics", 7, "Limit, Continuity and Differentiability", "सीमा, सांतत्य एवं अवकलनीयता", 1),
        unit("mathematics", 8, "Integral Calculus", "समाकलन गणित", 1),
        unit("mathematics", 9, "Differential Equations", "अवकल समीकरण", 2),
        unit("mathematics", 10, "Coordinate Geometry", "निर्देशांक ज्यामिति", 2),
        unit("mathematics", 11, "Three Dimensional Geometry", "त्रिविमीय ज्यामिति", 2),
        unit("mathematics", 12, "Vector Algebra", "सदिश बीजगणित", 2),
        unit("mathematics", 13, "Statistics and Probability", "सांख्यिकी एवं प्रायिकता", 2),
        unit("mathematics", 14, "Trigonometry", "त्रिकोणमिति", 2)
    )

    val physics = listOf(
        unit("physics", 1, "Units and Measurements", "मात्रक एवं मापन", 3),
        unit("physics", 2, "Kinematics", "गतिकी", 3),
        unit("physics", 3, "Laws of Motion", "गति के नियम", 3),
        unit("physics", 4, "Work, Energy and Power", "कार्य, ऊर्जा एवं शक्ति", 3),
        unit("physics", 5, "Rotational Motion", "घूर्णन गति", 3),
        unit("physics", 6, "Gravitation", "गुरुत्वाकर्षण", 3),
        unit("physics", 7, "Properties of Solids and Liquids", "ठोस एवं द्रव के गुण", 3),
        unit("physics", 8, "Thermodynamics", "ऊष्मागतिकी", 4),
        unit("physics", 9, "Kinetic Theory of Gases", "गैसों का गतिज सिद्धांत", 4),
        unit("physics", 10, "Oscillations and Waves", "दोलन एवं तरंगें", 4),
        unit("physics", 11, "Electrostatics", "स्थिर वैद्युतिकी", 4),
        unit("physics", 12, "Current Electricity", "विद्युत धारा", 5),
        unit("physics", 13, "Magnetic Effects of Current and Magnetism", "विद्युत धारा के चुंबकीय प्रभाव एवं चुंबकत्व", 5),
        unit("physics", 14, "Electromagnetic Induction and Alternating Currents", "विद्युत चुंबकीय प्रेरण एवं प्रत्यावर्ती धाराएँ", 5),
        unit("physics", 15, "Electromagnetic Waves", "विद्युत चुंबकीय तरंगें", 5),
        unit("physics", 16, "Optics", "प्रकाशिकी", 5),
        unit("physics", 17, "Dual Nature of Matter and Radiation", "पदार्थ एवं विकिरण की द्वैत प्रकृति", 6),
        unit("physics", 18, "Atoms and Nuclei", "परमाणु एवं नाभिक", 6),
        unit("physics", 19, "Electronic Devices", "इलेक्ट्रॉनिक उपकरण", 6),
        unit("physics", 20, "Experimental Skills", "प्रायोगिक कौशल", 6)
    )

    val chemistry = listOf(
        unit("chemistry", 1, "Some Basic Concepts in Chemistry", "रसायन विज्ञान की कुछ मूल अवधारणाएँ", 7),
        unit("chemistry", 2, "Atomic Structure", "परमाणु संरचना", 7),
        unit("chemistry", 3, "Chemical Bonding and Molecular Structure", "रासायनिक बंधन एवं आणविक संरचना", 7),
        unit("chemistry", 4, "Chemical Thermodynamics", "रासायनिक ऊष्मागतिकी", 7),
        unit("chemistry", 5, "Solutions", "विलयन", 7),
        unit("chemistry", 6, "Equilibrium", "साम्यावस्था", 8),
        unit("chemistry", 7, "Redox Reactions and Electrochemistry", "रेडॉक्स अभिक्रियाएँ एवं वैद्युत रसायन", 8),
        unit("chemistry", 8, "Chemical Kinetics", "रासायनिक गतिकी", 8),
        unit("chemistry", 9, "Classification of Elements and Periodicity in Properties", "तत्वों का वर्गीकरण एवं गुणों में आवर्तिता", 8),
        unit("chemistry", 10, "p-Block Elements", "पी-ब्लॉक तत्व", 8),
        unit("chemistry", 11, "d- and f-Block Elements", "डी- एवं एफ-ब्लॉक तत्व", 8),
        unit("chemistry", 12, "Coordination Compounds", "उपसहसंयोजन यौगिक", 9),
        unit("chemistry", 13, "Purification and Characterisation of Organic Compounds", "कार्बनिक यौगिकों का शुद्धिकरण एवं अभिलक्षण", 9),
        unit("chemistry", 14, "Some Basic Principles of Organic Chemistry", "कार्बनिक रसायन के कुछ मूल सिद्धांत", 9),
        unit("chemistry", 15, "Hydrocarbons", "हाइड्रोकार्बन", 9),
        unit("chemistry", 16, "Organic Compounds Containing Halogens", "हैलोजन युक्त कार्बनिक यौगिक", 9),
        unit("chemistry", 17, "Organic Compounds Containing Oxygen", "ऑक्सीजन युक्त कार्बनिक यौगिक", 10),
        unit("chemistry", 18, "Organic Compounds Containing Nitrogen", "नाइट्रोजन युक्त कार्बनिक यौगिक", 10),
        unit("chemistry", 19, "Biomolecules", "जैव-अणु", 10),
        unit("chemistry", 20, "Principles Related to Practical Chemistry", "प्रायोगिक रसायन से संबंधित सिद्धांत", 10)
    )

    val allUnits: List<SyllabusUnitIndexEntry> = mathematics + physics + chemistry

    fun unitsForSubject(subjectId: String): List<SyllabusUnitIndexEntry> =
        when (subjectId) {
            "physics" -> physics
            "chemistry" -> chemistry
            "mathematics" -> mathematics
            else -> emptyList()
        }

    fun hasExpectedUnitCounts(): Boolean =
        physics.size == 20 && chemistry.size == 20 && mathematics.size == 14 &&
            allUnits.size == 54 &&
            allUnits.map { it.unitId }.distinct().size == allUnits.size
}

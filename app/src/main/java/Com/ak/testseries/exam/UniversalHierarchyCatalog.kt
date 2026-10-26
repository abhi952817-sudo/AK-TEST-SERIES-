package Com.ak.testseries.exam

import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity

/**
 * Universal Hierarchy Catalog supporting the 6-step Test Preparation Flow:
 * Exam -> Subject -> Chapter -> Topic -> [Practice Tests & PYQs]
 * Complete with authentic bilingual topic trees, practice sets, and previous year papers.
 */
data class ExamHierarchyItem(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val category: String,
    val subjects: List<SubjectHierarchyItem>
)

data class SubjectHierarchyItem(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val chaptersCount: Int,
    val chapters: List<ChapterHierarchyItem>,
    val fullSubjectTest: TestMetaInfo
)

data class ChapterHierarchyItem(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val topicsCount: Int,
    val weightagePercent: Int,
    val topics: List<TopicHierarchyItem>,
    val fullChapterTest: TestMetaInfo
)

data class TopicHierarchyItem(
    val id: String,
    val nameEn: String,
    val nameHi: String,
    val mcqCount: Int,
    val practiceTests: List<TestMetaInfo>,
    val pyqTests: List<TestMetaInfo>
)

data class TestMetaInfo(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val mcqCount: Int,
    val durationMinutes: Int,
    val isPyq: Boolean = false,
    val year: Int? = null
)

object UniversalHierarchyCatalog {

    fun getAllExams(): List<ExamHierarchyItem> {
        return listOf(
            getJeeMainsHierarchy(),
            getJeeAdvancedHierarchy(),
            getNeetUgHierarchy(),
            getUpscHierarchy(),
            getSscCglHierarchy(),
            getClass11Hierarchy(),
            getClass12Hierarchy()
        )
    }

    fun getExamHierarchy(examId: String): ExamHierarchyItem {
        val upper = examId.uppercase()
        return when {
            upper.contains("ADVANCED") || upper == "JEE_ADVANCED" -> getJeeAdvancedHierarchy()
            upper.contains("JEE") -> getJeeMainsHierarchy()
            upper.contains("NEET") -> getNeetUgHierarchy()
            upper.contains("UPSC") || upper.contains("CDS") || upper.contains("NDA") -> getUpscHierarchy()
            upper.contains("SSC") || upper.contains("CGL") -> getSscCglHierarchy()
            upper.contains("11") -> getClass11Hierarchy()
            upper.contains("12") -> getClass12Hierarchy()
            else -> getNeetUgHierarchy()
        }
    }

    fun getJeeAdvancedHierarchy(): ExamHierarchyItem {
        return ExamHierarchyItem(
            id = "jee_advanced",
            nameEn = "JEE Advanced",
            nameHi = "जेईई एडवांस्ड",
            category = "Engineering",
            subjects = listOf(
                SubjectHierarchyItem(
                    id = "jeeadv_phy",
                    nameEn = "Physics",
                    nameHi = "भौतिक विज्ञान",
                    chaptersCount = 0,
                    chapters = emptyList(),
                    fullSubjectTest = TestMetaInfo("jeeadv_full_phy", "JEE Advanced Full Physics", "जेईई एडवांस्ड संपूर्ण भौतिकी", 18, 60)
                ),
                SubjectHierarchyItem(
                    id = "jeeadv_chem",
                    nameEn = "Chemistry",
                    nameHi = "रसायन विज्ञान",
                    chaptersCount = 0,
                    chapters = emptyList(),
                    fullSubjectTest = TestMetaInfo("jeeadv_full_chem", "JEE Advanced Full Chemistry", "जेईई एडवांस्ड संपूर्ण रसायन", 18, 60)
                ),
                SubjectHierarchyItem(
                    id = "jeeadv_math",
                    nameEn = "Mathematics",
                    nameHi = "गणित",
                    chaptersCount = 0,
                    chapters = emptyList(),
                    fullSubjectTest = TestMetaInfo("jeeadv_full_math", "JEE Advanced Full Mathematics", "जेईई एडवांस्ड संपूर्ण गणित", 18, 60)
                )
            )
        )
    }

    // =========================================================================
    // 1. JEE MAINS HIERARCHY
    // =========================================================================
    fun getJeeMainsHierarchy(): ExamHierarchyItem {
        val physicsChapters = listOf(
            ChapterHierarchyItem(
                id = "jee_phy_lom",
                nameEn = "Laws of Motion",
                nameHi = "गति के नियम",
                topicsCount = 4,
                weightagePercent = 8,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "jee_phy_lom_newton",
                        nameEn = "Newton's Laws",
                        nameHi = "न्यूटन के नियम",
                        mcqCount = 30,
                        practiceTests = listOf(
                            TestMetaInfo("jee_lom_newton_p1", "Newton's Laws Practice 1", "न्यूटन के नियम अभ्यास 1", 20, 25),
                            TestMetaInfo("jee_lom_newton_p2", "Newton's Laws Practice 2", "न्यूटन के नियम अभ्यास 2", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_lom_newton_pyq23", "Newton's Laws PYQ 2023", "न्यूटन के नियम PYQ 2023", 25, 30, true, 2023),
                            TestMetaInfo("jee_lom_newton_pyq22", "Newton's Laws PYQ 2022", "न्यूटन के नियम PYQ 2022", 20, 25, true, 2022),
                            TestMetaInfo("jee_lom_newton_pyq21", "Newton's Laws PYQ 2021", "न्यूटन के नियम PYQ 2021", 15, 20, true, 2021)
                        )
                    ),
                    TopicHierarchyItem(
                        id = "jee_phy_lom_friction",
                        nameEn = "Friction",
                        nameHi = "घर्षण",
                        mcqCount = 20,
                        practiceTests = listOf(
                            TestMetaInfo("jee_lom_friction_p1", "Friction Practice 1", "घर्षण अभ्यास 1", 20, 25),
                            TestMetaInfo("jee_lom_friction_p2", "Friction Practice 2", "घर्षण अभ्यास 2", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_lom_friction_pyq23", "Friction PYQ 2023", "घर्षण PYQ 2023", 30, 35, true, 2023),
                            TestMetaInfo("jee_lom_friction_pyq22", "Friction PYQ 2022", "घर्षण PYQ 2022", 10, 15, true, 2022),
                            TestMetaInfo("jee_lom_friction_pyq21", "Friction PYQ 2021", "घर्षण PYQ 2021", 10, 15, true, 2021)
                        )
                    ),
                    TopicHierarchyItem(
                        id = "jee_phy_lom_circular",
                        nameEn = "Circular Motion",
                        nameHi = "वृत्तीय गति",
                        mcqCount = 30,
                        practiceTests = listOf(
                            TestMetaInfo("jee_lom_circular_p1", "Circular Motion Practice 1", "वृत्तीय गति अभ्यास 1", 20, 25),
                            TestMetaInfo("jee_lom_circular_p2", "Circular Motion Practice 2", "वृत्तीय गति अभ्यास 2", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_lom_circular_pyq23", "Circular Motion PYQ 2023", "वृत्तीय गति PYQ 2023", 20, 25, true, 2023),
                            TestMetaInfo("jee_lom_circular_pyq22", "Circular Motion PYQ 2022", "वृत्तीय गति PYQ 2022", 15, 20, true, 2022)
                        )
                    ),
                    TopicHierarchyItem(
                        id = "jee_phy_lom_angular",
                        nameEn = "Connected Bodies & Equilibrium",
                        nameHi = "संबद्ध पिंड और संतुलन",
                        mcqCount = 15,
                        practiceTests = listOf(
                            TestMetaInfo("jee_lom_angular_p1", "Equilibrium Practice 1", "संतुलन अभ्यास 1", 15, 20)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_lom_angular_pyq23", "Equilibrium PYQ 2023", "संतुलन PYQ 2023", 15, 20, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("jee_phy_lom_full", "Laws of Motion Full Chapter Test", "गति के नियम पूर्ण अध्याय टेस्ट", 30, 45)
            ),
            ChapterHierarchyItem(
                id = "jee_phy_kinematics",
                nameEn = "Kinematics",
                nameHi = "गतिकी",
                topicsCount = 9,
                weightagePercent = 7,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "jee_phy_kin_1d",
                        nameEn = "1D Motion & Graphs",
                        nameHi = "एकविमीय गति एवं ग्राफ",
                        mcqCount = 25,
                        practiceTests = listOf(
                            TestMetaInfo("jee_kin_1d_p1", "1D Motion Practice 1", "एकविमीय गति अभ्यास 1", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_kin_1d_pyq23", "1D Motion PYQ 2023", "एकविमीय गति PYQ 2023", 20, 25, true, 2023)
                        )
                    ),
                    TopicHierarchyItem(
                        id = "jee_phy_kin_projectile",
                        nameEn = "Projectile Motion",
                        nameHi = "प्रक्षेप्य गति",
                        mcqCount = 25,
                        practiceTests = listOf(
                            TestMetaInfo("jee_kin_proj_p1", "Projectile Practice 1", "प्रक्षेप्य अभ्यास 1", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_kin_proj_pyq23", "Projectile PYQ 2023", "प्रक्षेप्य PYQ 2023", 20, 25, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("jee_phy_kin_full", "Kinematics Full Chapter Test", "गतिकी पूर्ण अध्याय टेस्ट", 30, 45)
            ),
            ChapterHierarchyItem(
                id = "jee_phy_gravitation",
                nameEn = "Gravitation",
                nameHi = "गुरुत्वाकर्षण",
                topicsCount = 3,
                weightagePercent = 6,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "jee_phy_grav_field",
                        nameEn = "Gravitational Field & Potential",
                        nameHi = "गुरुत्वीय क्षेत्र एवं विभव",
                        mcqCount = 20,
                        practiceTests = listOf(
                            TestMetaInfo("jee_grav_p1", "Gravitation Practice 1", "गुरुत्वाकर्षण अभ्यास 1", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_grav_pyq23", "Gravitation PYQ 2023", "गुरुत्वाकर्षण PYQ 2023", 20, 25, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("jee_phy_grav_full", "Gravitation Full Chapter Test", "गुरुत्वाकर्षण पूर्ण अध्याय टेस्ट", 25, 35)
            ),
            ChapterHierarchyItem(
                id = "jee_phy_rotational",
                nameEn = "Rotational Dynamics",
                nameHi = "घूर्णन गति",
                topicsCount = 5,
                weightagePercent = 10,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "jee_phy_rot_moi",
                        nameEn = "Moment of Inertia & Torque",
                        nameHi = "जड़त्व आघूर्ण एवं बल आघूर्ण",
                        mcqCount = 30,
                        practiceTests = listOf(
                            TestMetaInfo("jee_rot_p1", "Rotational Dynamics Practice 1", "घूर्णन गति अभ्यास 1", 20, 30)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_rot_pyq23", "Rotational Dynamics PYQ 2023", "घूर्णन गति PYQ 2023", 25, 35, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("jee_phy_rot_full", "Rotational Dynamics Full Chapter Test", "घूर्णन गति पूर्ण अध्याय टेस्ट", 30, 45)
            )
        )

        val chemistryChapters = listOf(
            ChapterHierarchyItem(
                id = "jee_chem_bonding",
                nameEn = "Chemical Bonding & MOT",
                nameHi = "रासायनिक आबंधन एवं MOT",
                topicsCount = 4,
                weightagePercent = 10,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "jee_chem_vsepr",
                        nameEn = "VSEPR & Hybridization",
                        nameHi = "VSEPR एवं संकरण",
                        mcqCount = 25,
                        practiceTests = listOf(
                            TestMetaInfo("jee_chem_vsepr_p1", "VSEPR Practice 1", "VSEPR अभ्यास 1", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_chem_vsepr_pyq23", "VSEPR PYQ 2023", "VSEPR PYQ 2023", 20, 25, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("jee_chem_bonding_full", "Chemical Bonding Full Chapter Test", "रासायनिक आबंधन पूर्ण अध्याय टेस्ट", 30, 45)
            ),
            ChapterHierarchyItem(
                id = "jee_chem_thermo",
                nameEn = "Thermodynamics",
                nameHi = "ऊष्मागतिकी",
                topicsCount = 4,
                weightagePercent = 8,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "jee_chem_thermo_first",
                        nameEn = "First Law & Enthalpy",
                        nameHi = "प्रथम नियम एवं एन्थैल्पी",
                        mcqCount = 20,
                        practiceTests = listOf(
                            TestMetaInfo("jee_thermo_p1", "Thermodynamics Practice 1", "ऊष्मागतिकी अभ्यास 1", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_thermo_pyq23", "Thermodynamics PYQ 2023", "ऊष्मागतिकी PYQ 2023", 20, 25, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("jee_chem_thermo_full", "Thermodynamics Full Chapter Test", "ऊष्मागतिकी पूर्ण अध्याय टेस्ट", 30, 45)
            )
        )

        val mathsChapters = listOf(
            ChapterHierarchyItem(
                id = "jee_math_calculus",
                nameEn = "Definite Integrals & Area",
                nameHi = "निश्चित समाकलन एवं क्षेत्रफल",
                topicsCount = 5,
                weightagePercent = 12,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "jee_math_def_int",
                        nameEn = "Properties of Definite Integrals",
                        nameHi = "निश्चित समाकलन के गुणधर्म",
                        mcqCount = 30,
                        practiceTests = listOf(
                            TestMetaInfo("jee_def_int_p1", "Definite Integrals Practice 1", "निश्चित समाकलन अभ्यास 1", 20, 30)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_def_int_pyq23", "Definite Integrals PYQ 2023", "निश्चित समाकलन PYQ 2023", 25, 35, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("jee_math_def_int_full", "Definite Integrals Full Chapter Test", "निश्चित समाकलन पूर्ण अध्याय टेस्ट", 30, 50)
            ),
            ChapterHierarchyItem(
                id = "jee_math_vectors_3d",
                nameEn = "Vectors & 3D Geometry",
                nameHi = "सदिश एवं त्रिविमीय ज्यामिति",
                topicsCount = 4,
                weightagePercent = 12,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "jee_math_3d_lines",
                        nameEn = "Lines & Planes in 3D",
                        nameHi = "3D में रेखाएँ एवं समतल",
                        mcqCount = 25,
                        practiceTests = listOf(
                            TestMetaInfo("jee_3d_p1", "3D Geometry Practice 1", "3D ज्यामिति अभ्यास 1", 20, 30)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("jee_3d_pyq23", "3D Geometry PYQ 2023", "3D ज्यामिति PYQ 2023", 20, 30, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("jee_math_3d_full", "Vectors & 3D Full Chapter Test", "सदिश एवं 3D पूर्ण अध्याय टेस्ट", 30, 50)
            )
        )

        val subjects = listOf(
            SubjectHierarchyItem(
                id = "jee_subj_physics",
                nameEn = "Physics",
                nameHi = "भौतिकी",
                chaptersCount = physicsChapters.size,
                chapters = physicsChapters,
                fullSubjectTest = TestMetaInfo("jee_phy_full_subj", "JEE Main Full Physics Test", "जेईई मेन्स पूर्ण भौतिकी टेस्ट", 30, 60)
            ),
            SubjectHierarchyItem(
                id = "jee_subj_chemistry",
                nameEn = "Chemistry",
                nameHi = "रसायन",
                chaptersCount = chemistryChapters.size,
                chapters = chemistryChapters,
                fullSubjectTest = TestMetaInfo("jee_chem_full_subj", "JEE Main Full Chemistry Test", "जेईई मेन्स पूर्ण रसायन टेस्ट", 30, 60)
            ),
            SubjectHierarchyItem(
                id = "jee_subj_maths",
                nameEn = "Maths",
                nameHi = "गणित",
                chaptersCount = mathsChapters.size,
                chapters = mathsChapters,
                fullSubjectTest = TestMetaInfo("jee_math_full_subj", "JEE Main Full Maths Test", "जेईई मेन्स पूर्ण गणित टेस्ट", 30, 60)
            )
        )

        return ExamHierarchyItem(
            id = "jee_mains",
            nameEn = "JEE Mains",
            nameHi = "जेईई मेन्स",
            category = "Engineering Entrance",
            subjects = subjects
        )
    }

    // =========================================================================
    // 2. NEET UG HIERARCHY
    // =========================================================================
    fun getNeetUgHierarchy(): ExamHierarchyItem {
        val physicsChapters = listOf(
            ChapterHierarchyItem(
                id = "neet_phy_optics",
                nameEn = "Ray & Wave Optics",
                nameHi = "किरण एवं तरंग प्रकाशिकी",
                topicsCount = 4,
                weightagePercent = 10,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "neet_phy_ray_optics",
                        nameEn = "Refraction & Lenses",
                        nameHi = "अपवर्तन एवं लेंस",
                        mcqCount = 25,
                        practiceTests = listOf(
                            TestMetaInfo("neet_optics_p1", "Ray Optics Practice 1", "किरण प्रकाशिकी अभ्यास 1", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("neet_optics_pyq23", "Optics PYQ 2023", "प्रकाशिकी PYQ 2023", 25, 30, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("neet_optics_full", "Optics Full Chapter Test", "प्रकाशिकी पूर्ण अध्याय टेस्ट", 45, 45)
            )
        )

        val chemistryChapters = listOf(
            ChapterHierarchyItem(
                id = "neet_chem_organic",
                nameEn = "Organic Chemistry (GOC)",
                nameHi = "कार्बनिक रसायन (GOC)",
                topicsCount = 4,
                weightagePercent = 9,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "neet_chem_goc_basics",
                        nameEn = "Inductive & Resonance Effects",
                        nameHi = "प्रेरणिक एवं अनुनाद प्रभाव",
                        mcqCount = 30,
                        practiceTests = listOf(
                            TestMetaInfo("neet_goc_p1", "GOC Practice 1", "GOC अभ्यास 1", 20, 25)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("neet_goc_pyq23", "GOC PYQ 2023", "GOC PYQ 2023", 20, 25, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("neet_goc_full", "GOC Full Chapter Test", "GOC पूर्ण अध्याय टेस्ट", 45, 45)
            )
        )

        val botanyChapters = listOf(
            ChapterHierarchyItem(
                id = "neet_bot_genetics",
                nameEn = "Genetics & Inheritance",
                nameHi = "आनुवंशिकी एवं वंशागति",
                topicsCount = 4,
                weightagePercent = 12,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "neet_gen_mendel",
                        nameEn = "Mendelian Crosses",
                        nameHi = "मेंडेलियन संकरण",
                        mcqCount = 30,
                        practiceTests = listOf(
                            TestMetaInfo("neet_gen_p1", "Genetics Practice 1", "आनुवंशिकी अभ्यास 1", 25, 30)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("neet_gen_pyq23", "Genetics PYQ 2023", "आनुवंशिकी PYQ 2023", 30, 35, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("neet_gen_full", "Genetics Full Chapter Test", "आनुवंशिकी पूर्ण अध्याय टेस्ट", 50, 50)
            )
        )

        val zoologyChapters = listOf(
            ChapterHierarchyItem(
                id = "neet_zoo_physio",
                nameEn = "Human Physiology",
                nameHi = "मानव शरीर क्रिया विज्ञान",
                topicsCount = 6,
                weightagePercent = 14,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "neet_zoo_circ",
                        nameEn = "Circulation & Heart",
                        nameHi = "परिसंचरण एवं हृदय",
                        mcqCount = 30,
                        practiceTests = listOf(
                            TestMetaInfo("neet_circ_p1", "Circulation Practice 1", "परिसंचरण अभ्यास 1", 25, 30)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("neet_circ_pyq23", "Circulation PYQ 2023", "परिसंचरण PYQ 2023", 25, 30, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("neet_zoo_physio_full", "Human Physiology Full Chapter Test", "मानव शरीर क्रिया विज्ञान पूर्ण अध्याय टेस्ट", 50, 50)
            )
        )

        val subjects = listOf(
            SubjectHierarchyItem("neet_subj_phy", "Physics", "भौतिकी", physicsChapters.size, physicsChapters,
                TestMetaInfo("neet_phy_full_subj", "NEET Full Physics Test", "नीट पूर्ण भौतिकी टेस्ट", 50, 60)),
            SubjectHierarchyItem("neet_subj_chem", "Chemistry", "रसायन", chemistryChapters.size, chemistryChapters,
                TestMetaInfo("neet_chem_full_subj", "NEET Full Chemistry Test", "नीट पूर्ण रसायन टेस्ट", 50, 60)),
            SubjectHierarchyItem("neet_subj_bot", "Botany", "वनस्पति विज्ञान", botanyChapters.size, botanyChapters,
                TestMetaInfo("neet_bot_full_subj", "NEET Full Botany Test", "नीट पूर्ण वनस्पति विज्ञान टेस्ट", 50, 60)),
            SubjectHierarchyItem("neet_subj_zoo", "Zoology", "प्राणी विज्ञान", zoologyChapters.size, zoologyChapters,
                TestMetaInfo("neet_zoo_full_subj", "NEET Full Zoology Test", "नीट पूर्ण प्राणी विज्ञान टेस्ट", 50, 60))
        )

        return ExamHierarchyItem(
            id = "neet_ug",
            nameEn = "NEET",
            nameHi = "नीट",
            category = "Medical Entrance",
            subjects = subjects
        )
    }

    // =========================================================================
    // 3. UPSC HIERARCHY
    // =========================================================================
    fun getUpscHierarchy(): ExamHierarchyItem {
        val gkChapters = listOf(
            ChapterHierarchyItem(
                id = "upsc_gk_polity",
                nameEn = "Indian Polity & Constitution",
                nameHi = "भारतीय राजव्यवस्था एवं संविधान",
                topicsCount = 5,
                weightagePercent = 20,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "upsc_gk_fr",
                        nameEn = "Fundamental Rights & DPSP",
                        nameHi = "मौलिक अधिकार एवं नीति निदेशक तत्व",
                        mcqCount = 30,
                        practiceTests = listOf(
                            TestMetaInfo("upsc_fr_p1", "Polity Practice 1", "राजव्यवस्था अभ्यास 1", 25, 30)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("upsc_fr_pyq23", "Polity PYQ 2023", "राजव्यवस्था PYQ 2023", 25, 30, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("upsc_polity_full", "Polity Full Chapter Test", "राजव्यवस्था पूर्ण अध्याय टेस्ट", 50, 60)
            )
        )

        val subjects = listOf(
            SubjectHierarchyItem("upsc_subj_gk", "General Knowledge", "सामान्य ज्ञान", gkChapters.size, gkChapters,
                TestMetaInfo("upsc_gk_full_subj", "UPSC Full GK Test", "यूपीएससी पूर्ण सामान्य ज्ञान टेस्ट", 50, 60)),
            SubjectHierarchyItem("upsc_subj_eng", "English", "अंग्रेजी", 4, emptyList(),
                TestMetaInfo("upsc_eng_full_subj", "UPSC Full English Test", "यूपीएससी पूर्ण अंग्रेजी टेस्ट", 50, 60)),
            SubjectHierarchyItem("upsc_subj_maths", "Elementary Maths", "प्रारंभिक गणित", 5, emptyList(),
                TestMetaInfo("upsc_math_full_subj", "UPSC Full Maths Test", "यूपीएससी पूर्ण गणित टेस्ट", 50, 60))
        )

        return ExamHierarchyItem(
            id = "upsc_cds",
            nameEn = "UPSC",
            nameHi = "यूपीएससी",
            category = "Civil & Defense",
            subjects = subjects
        )
    }

    // =========================================================================
    // 4. SSC CGL HIERARCHY
    // =========================================================================
    fun getSscCglHierarchy(): ExamHierarchyItem {
        val quantChapters = listOf(
            ChapterHierarchyItem(
                id = "ssc_quant_arithmetic",
                nameEn = "Percentage & Profit/Loss",
                nameHi = "प्रतिशत एवं लाभ/हानि",
                topicsCount = 4,
                weightagePercent = 18,
                topics = listOf(
                    TopicHierarchyItem(
                        id = "ssc_quant_profit_loss",
                        nameEn = "Profit, Loss & Discount",
                        nameHi = "लाभ, हानि एवं छूट",
                        mcqCount = 25,
                        practiceTests = listOf(
                            TestMetaInfo("ssc_pl_p1", "Profit & Loss Practice 1", "लाभ एवं हानि अभ्यास 1", 20, 20)
                        ),
                        pyqTests = listOf(
                            TestMetaInfo("ssc_pl_pyq23", "Profit & Loss PYQ 2023", "लाभ एवं हानि PYQ 2023", 25, 25, true, 2023)
                        )
                    )
                ),
                fullChapterTest = TestMetaInfo("ssc_pl_full", "Profit & Loss Full Test", "लाभ एवं हानि पूर्ण टेस्ट", 30, 30)
            )
        )

        val subjects = listOf(
            SubjectHierarchyItem("ssc_subj_quant", "Quantitative Aptitude", "मात्रात्मक योग्यता", quantChapters.size, quantChapters,
                TestMetaInfo("ssc_quant_full_subj", "SSC Full Quant Test", "एसएससी पूर्ण गणित टेस्ट", 25, 25)),
            SubjectHierarchyItem("ssc_subj_reasoning", "Reasoning", "तर्कशक्ति", 5, emptyList(),
                TestMetaInfo("ssc_reasoning_full_subj", "SSC Full Reasoning Test", "एसएससी पूर्ण तर्कशक्ति टेस्ट", 25, 25)),
            SubjectHierarchyItem("ssc_subj_eng", "English", "अंग्रेजी", 5, emptyList(),
                TestMetaInfo("ssc_eng_full_subj", "SSC Full English Test", "एसएससी पूर्ण अंग्रेजी टेस्ट", 25, 25)),
            SubjectHierarchyItem("ssc_subj_ga", "General Awareness", "सामान्य जागरूकता", 6, emptyList(),
                TestMetaInfo("ssc_ga_full_subj", "SSC Full GA Test", "एसएससी पूर्ण जागरूकता टेस्ट", 25, 25))
        )

        return ExamHierarchyItem(
            id = "ssc_cgl",
            nameEn = "SSC CGL",
            nameHi = "एसएससी सीजीएल",
            category = "Staff Selection",
            subjects = subjects
        )
    }

    // =========================================================================
    // 5. CLASS 11 & CLASS 12 HIERARCHY
    // =========================================================================
    fun getClass11Hierarchy(): ExamHierarchyItem {
        return ExamHierarchyItem(
            id = "class_11",
            nameEn = "Class 11",
            nameHi = "कक्षा 11",
            category = "School Foundation",
            subjects = getJeeMainsHierarchy().subjects
        )
    }

    fun getClass12Hierarchy(): ExamHierarchyItem {
        return ExamHierarchyItem(
            id = "class_12",
            nameEn = "Class 12",
            nameHi = "कक्षा 12",
            category = "Board & Entrance",
            subjects = getNeetUgHierarchy().subjects
        )
    }

    // =========================================================================
    // REAL TEST LINKING FROM FIREBASE & ROOM DATABASE (No dummy data)
    // =========================================================================
    fun getRealTestsForTopic(
        allTests: List<TestEntity>,
        examName: String,
        subjectName: String,
        chapterName: String,
        topicName: String
    ): Pair<List<TestEntity>, List<TestEntity>> {
        val matching = allTests.filter { test ->
            val examMatch = test.targetExam.contains(examName, ignoreCase = true) || examName.contains(test.targetExam, ignoreCase = true) || test.targetExam.equals("All", ignoreCase = true)
            val subjMatch = test.subject.contains(subjectName, ignoreCase = true) || subjectName.contains(test.subject, ignoreCase = true) || test.subject.equals("All", ignoreCase = true)
            val chapMatch = test.chapter.contains(chapterName, ignoreCase = true) || chapterName.contains(test.chapter, ignoreCase = true) || test.chapter.equals("All", ignoreCase = true)
            val topicMatch = test.topic.contains(topicName, ignoreCase = true) || topicName.contains(test.topic, ignoreCase = true) || test.topic.isBlank() || test.topic.equals("All", ignoreCase = true)
            examMatch && subjMatch && (chapMatch || topicMatch)
        }
        val pyqs = matching.filter { it.testType.contains("PYQ", ignoreCase = true) || it.title.contains("PYQ", ignoreCase = true) }
        val practice = matching.filter { !it.testType.contains("PYQ", ignoreCase = true) && !it.title.contains("PYQ", ignoreCase = true) }
        return Pair(practice, pyqs)
    }

    fun getRealTestsForChapter(
        allTests: List<TestEntity>,
        examName: String,
        subjectName: String,
        chapterName: String
    ): List<TestEntity> {
        return allTests.filter { test ->
            val examMatch = test.targetExam.contains(examName, ignoreCase = true) || examName.contains(test.targetExam, ignoreCase = true) || test.targetExam.equals("All", ignoreCase = true)
            val subjMatch = test.subject.contains(subjectName, ignoreCase = true) || subjectName.contains(test.subject, ignoreCase = true) || test.subject.equals("All", ignoreCase = true)
            val chapMatch = test.chapter.contains(chapterName, ignoreCase = true) || chapterName.contains(test.chapter, ignoreCase = true) || test.chapter.equals("All", ignoreCase = true)
            examMatch && subjMatch && chapMatch
        }
    }

    fun getRealTestsForSubject(
        allTests: List<TestEntity>,
        examName: String,
        subjectName: String
    ): List<TestEntity> {
        return allTests.filter { test ->
            val examMatch = test.targetExam.contains(examName, ignoreCase = true) || examName.contains(test.targetExam, ignoreCase = true) || test.targetExam.equals("All", ignoreCase = true)
            val subjMatch = test.subject.contains(subjectName, ignoreCase = true) || subjectName.contains(test.subject, ignoreCase = true) || test.subject.equals("All", ignoreCase = true)
            examMatch && subjMatch
        }
    }
}

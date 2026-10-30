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
            getNeetUgHierarchy(),
            getJeeMainsHierarchy(),
            getJeeAdvancedHierarchy(),
            getBankingHierarchy(),
            getRailwaysHierarchy(),
            getSscCglHierarchy(),
            getUpscHierarchy(),
            getUgcNetHierarchy(),
            getCatHierarchy(),
            getCuetHierarchy(),
            getClass11Hierarchy(),
            getClass12Hierarchy()
        )
    }

    fun getExamHierarchy(examId: String): ExamHierarchyItem {
        val upper = examId.uppercase()
        return when {
            upper.contains("ADVANCED") || upper == "JEE_ADVANCED" -> getJeeAdvancedHierarchy()
            upper.contains("JEE") -> getJeeMainsHierarchy()
            upper.contains("NEET") || upper.contains("MEDICAL") -> getNeetUgHierarchy()
            upper.contains("BANK") || upper.contains("IBPS") || upper.contains("SBI") -> getBankingHierarchy()
            upper.contains("RAILWAY") || upper.contains("RRB") || upper.contains("NTPC") || upper.contains("ALP") || upper.contains("GROUP D") -> getRailwaysHierarchy()
            upper.contains("UGC") || upper.contains("NET") || upper.contains("CTET") -> getUgcNetHierarchy()
            upper.contains("CAT") || upper.contains("MANAGEMENT") -> getCatHierarchy()
            upper.contains("CUET") -> getCuetHierarchy()
            upper.contains("UPSC") || upper.contains("CDS") || upper.contains("NDA") || upper.contains("CIVIL") -> getUpscHierarchy()
            upper.contains("SSC") || upper.contains("CGL") || upper.contains("CHSL") || upper.contains("MTS") -> getSscCglHierarchy()
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
    // 6. BANKING EXAMS HIERARCHY (IBPS & SBI PO / CLERK)
    // =========================================================================
    fun getBankingHierarchy(): ExamHierarchyItem {
        val quantChapters = listOf(
            ChapterHierarchyItem(
                id = "bank_quant_di",
                nameEn = "Data Interpretation (DI)",
                nameHi = "डेटा इंटरप्रिटेशन (आँकड़ों का विश्लेषण)",
                topicsCount = 5,
                weightagePercent = 35,
                topics = listOf(
                    TopicHierarchyItem("bank_di_bar", "Bar & Line Graph DI", "बार एवं लाइन ग्राफ डीआई", 25, listOf(TestMetaInfo("b_di_1", "Bar Graph DI Practice", "बार ग्राफ अभ्यास", 10, 10)), emptyList()),
                    TopicHierarchyItem("bank_di_pie", "Pie Chart & Caselet DI", "पाई चार्ट एवं केसलेट डीआई", 30, listOf(TestMetaInfo("b_di_2", "Pie & Caselet Mastery", "पाई व केसलेट अभ्यास", 10, 10)), emptyList()),
                    TopicHierarchyItem("bank_di_radar", "Missing & Radar DI", "मिसिंग एवं राडार डीआई", 20, listOf(TestMetaInfo("b_di_3", "Advanced Missing DI", "मिसिंग डीआई टेस्ट", 10, 12)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("bank_full_di", "Complete DI Speed Test", "संपूर्ण डीआई स्पीड टेस्ट", 25, 20)
            ),
            ChapterHierarchyItem(
                id = "bank_quant_num",
                nameEn = "Number Series & Quadratic Equations",
                nameHi = "संख्या श्रृंखला एवं द्विघात समीकरण",
                topicsCount = 4,
                weightagePercent = 25,
                topics = listOf(
                    TopicHierarchyItem("bank_ns_miss", "Missing Number Series", "मिसिंग नंबर सीरीज", 30, listOf(TestMetaInfo("b_ns_1", "Missing Series 2-Min Speed", "मिसिंग सीरीज स्पीड", 10, 5)), emptyList()),
                    TopicHierarchyItem("bank_ns_wrong", "Wrong Number Series", "रॉन्ग नंबर सीरीज", 25, listOf(TestMetaInfo("b_ns_2", "Wrong Number Series Drill", "रॉन्ग सीरीज टेस्ट", 10, 7)), emptyList()),
                    TopicHierarchyItem("bank_quad_eq", "Quadratic Inequalities (x vs y)", "द्विघात समीकरण असमानताएँ", 30, listOf(TestMetaInfo("b_quad_1", "Sign Method Quadratic Speed", "द्विघात समीकरण अभ्यास", 10, 5)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("bank_full_speed_math", "Speed Math Full Test", "स्पीड मैथ संपूर्ण टेस्ट", 20, 12)
            ),
            ChapterHierarchyItem(
                id = "bank_quant_arith",
                nameEn = "Arithmetic Word Problems",
                nameHi = "अंकगणित व्यावहारिक प्रश्न",
                topicsCount = 6,
                weightagePercent = 40,
                topics = listOf(
                    TopicHierarchyItem("bank_arith_tsw", "Time & Work, Pipes & Cisterns", "समय, कार्य एवं नल-टंकी", 25, listOf(TestMetaInfo("b_arith_1", "Time & Work Practice", "समय और कार्य अभ्यास", 10, 10)), emptyList()),
                    TopicHierarchyItem("bank_arith_tsd", "Time, Speed & Distance, Trains", "चाल, समय, दूरी एवं रेलगाड़ी", 25, listOf(TestMetaInfo("b_arith_2", "TSD & Trains Drill", "चाल व दूरी टेस्ट", 10, 10)), emptyList()),
                    TopicHierarchyItem("bank_arith_si_ci", "Simple & Compound Interest", "साधारण एवं चक्रवृद्धि ब्याज", 20, listOf(TestMetaInfo("b_arith_3", "SI & CI Concepts", "ब्याज अभ्यास", 10, 10)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("bank_full_arith", "Arithmetic Comprehensive Test", "अंकगणित संपूर्ण टेस्ट", 25, 25)
            )
        )

        val reasoningChapters = listOf(
            ChapterHierarchyItem(
                id = "bank_reas_puzzles",
                nameEn = "Puzzles & Seating Arrangement",
                nameHi = "पहेलियाँ एवं बैठक व्यवस्था",
                topicsCount = 5,
                weightagePercent = 50,
                topics = listOf(
                    TopicHierarchyItem("bank_puz_circ", "Circular & Square Seating", "वृत्ताकार एवं वर्गाकार बैठक व्यवस्था", 30, listOf(TestMetaInfo("b_puz_1", "Circular Seating Drill", "वृत्ताकार व्यवस्था टेस्ट", 10, 10)), emptyList()),
                    TopicHierarchyItem("bank_puz_linear", "Linear Row & Parallel Rows", "समानांतर पंक्तियाँ व्यवस्था", 25, listOf(TestMetaInfo("b_puz_2", "Linear Rows Practice", "रेखीय पंक्ति अभ्यास", 10, 10)), emptyList()),
                    TopicHierarchyItem("bank_puz_floor", "Floor & Flat-Based Puzzles", "फ्लोर एवं फ्लैट पहेली", 25, listOf(TestMetaInfo("b_puz_3", "Floor Flat Puzzle Test", "फ्लोर पहेली अभ्यास", 10, 12)), emptyList()),
                    TopicHierarchyItem("bank_puz_box", "Box, Day & Month Puzzles", "बॉक्स, दिन एवं माह आधारित पहेली", 25, listOf(TestMetaInfo("b_puz_4", "Scheduling Puzzles", "शेड्यूलिंग पहेली", 10, 10)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("bank_full_puzzle", "Puzzles Speed Test", "पहेली स्पीड टेस्ट", 20, 15)
            ),
            ChapterHierarchyItem(
                id = "bank_reas_misc",
                nameEn = "Syllogism, Inequalities & Coding",
                nameHi = "न्याय निगमन, असमानता एवं कोडिंग",
                topicsCount = 4,
                weightagePercent = 50,
                topics = listOf(
                    TopicHierarchyItem("bank_reas_syllo", "Only a Few Syllogisms", "ओनली ए फ्यू सिलोगिज्म", 30, listOf(TestMetaInfo("b_syl_1", "Syllogism Mastery", "सिलोगिज्म टेस्ट", 10, 5)), emptyList()),
                    TopicHierarchyItem("bank_reas_ineq", "Coded & Direct Inequalities", "असमानताएँ", 25, listOf(TestMetaInfo("b_ineq_1", "Inequality Speed Run", "असमानता अभ्यास", 10, 5)), emptyList()),
                    TopicHierarchyItem("bank_reas_blood", "Blood Relations & Direction Sense", "रक्त संबंध एवं दिशा ज्ञान", 20, listOf(TestMetaInfo("b_br_1", "Blood Relations Drill", "रक्त संबंध टेस्ट", 10, 8)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("bank_full_misc_reas", "Reasoning Standalone Topics", "तर्कशक्ति विविध विषय टेस्ट", 20, 12)
            )
        )

        val englishChapters = listOf(
            ChapterHierarchyItem(
                id = "bank_eng_rc",
                nameEn = "Reading Comprehension",
                nameHi = "पठन बोध (आरसी)",
                topicsCount = 3,
                weightagePercent = 40,
                topics = listOf(
                    TopicHierarchyItem("bank_rc_eco", "Banking & Economic Passages", "बैंकिंग एवं आर्थिक परिच्छेद", 20, listOf(TestMetaInfo("b_rc_1", "Economy RC Drill", "इकोनॉमी आरसी टेस्ट", 8, 10)), emptyList()),
                    TopicHierarchyItem("bank_rc_story", "Inference & Tone Questions", "निष्कर्ष एवं टोन आधारित प्रश्न", 15, listOf(TestMetaInfo("b_rc_2", "Inference RC Drill", "इनफेरेंस आरसी", 8, 10)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("bank_full_rc", "RC Full Test", "आरसी संपूर्ण टेस्ट", 15, 15)
            ),
            ChapterHierarchyItem(
                id = "bank_eng_grammar",
                nameEn = "Error Spotting & Cloze Test",
                nameHi = "त्रुटि पहचान एवं क्लोज़ टेस्ट",
                topicsCount = 4,
                weightagePercent = 60,
                topics = listOf(
                    TopicHierarchyItem("bank_err_spot", "Grammar Error Detection", "व्याकरण त्रुटि पहचान", 30, listOf(TestMetaInfo("b_err_1", "Error Detection Practice", "एरर डिटेक्शन टेस्ट", 10, 7)), emptyList()),
                    TopicHierarchyItem("bank_cloze", "Cloze Test & Fillers", "क्लोज़ टेस्ट एवं फिलर्स", 25, listOf(TestMetaInfo("b_clz_1", "Cloze Test Drill", "क्लोज़ टेस्ट", 10, 8)), emptyList()),
                    TopicHierarchyItem("bank_parajumble", "Para Jumbles & Sentence Rearrangement", "पैरा जंबल्स", 20, listOf(TestMetaInfo("b_pj_1", "Para Jumbles Practice", "पैरा जंबल्स टेस्ट", 5, 6)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("bank_full_grammar", "Grammar & Vocab Full Test", "व्याकरण व शब्दकोश टेस्ट", 20, 15)
            )
        )

        return ExamHierarchyItem(
            id = "banking_exams",
            nameEn = "Banking (IBPS & SBI PO/Clerk)",
            nameHi = "बैंकिंग एवं वित्तीय भर्ती परीक्षाएँ",
            category = "Banking & Finance",
            subjects = listOf(
                SubjectHierarchyItem("bank_quant", "Quantitative Aptitude", "मात्रात्मक अभिरुचि", quantChapters.size, quantChapters, TestMetaInfo("b_full_quant", "Full Quant Mock", "पूर्ण गणित मॉक", 35, 20)),
                SubjectHierarchyItem("bank_reasoning", "Reasoning Ability", "तर्कशक्ति क्षमता", reasoningChapters.size, reasoningChapters, TestMetaInfo("b_full_reas", "Full Reasoning Mock", "पूर्ण तर्कशक्ति मॉक", 35, 20)),
                SubjectHierarchyItem("bank_english", "English Language", "अंग्रेजी भाषा", englishChapters.size, englishChapters, TestMetaInfo("b_full_eng", "Full English Mock", "पूर्ण अंग्रेजी मॉक", 30, 20))
            )
        )
    }

    // =========================================================================
    // 7. RAILWAYS EXAMS HIERARCHY (RRB NTPC, GROUP D, ALP)
    // =========================================================================
    fun getRailwaysHierarchy(): ExamHierarchyItem {
        val mathChapters = listOf(
            ChapterHierarchyItem(
                id = "rrb_math_basic",
                nameEn = "Number System, Decimals & Fractions",
                nameHi = "संख्या पद्धति, दशमलव एवं भिन्न",
                topicsCount = 4,
                weightagePercent = 30,
                topics = listOf(
                    TopicHierarchyItem("rrb_num_sys", "BODMAS & Divisibility", "बोडमास एवं विभाज्यता", 25, listOf(TestMetaInfo("r_num_1", "BODMAS Speed Test", "बोडमास टेस्ट", 10, 8)), emptyList()),
                    TopicHierarchyItem("rrb_lcm_hcf", "LCM and HCF Problems", "ल.स.प. एवं म.स.प.", 20, listOf(TestMetaInfo("r_lcm_1", "LCM & HCF Drill", "लसप मसप टेस्ट", 10, 8)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("rrb_full_basic_math", "Basic Math Full Test", "आधारभूत गणित संपूर्ण टेस्ट", 20, 18)
            ),
            ChapterHierarchyItem(
                id = "rrb_math_commercial",
                nameEn = "Commercial Math & Mensuration",
                nameHi = "व्यावसायिक गणित एवं क्षेत्रमिति",
                topicsCount = 5,
                weightagePercent = 70,
                topics = listOf(
                    TopicHierarchyItem("rrb_perc_pl", "Percentage, Profit & Loss", "प्रतिशत, लाभ एवं हानि", 30, listOf(TestMetaInfo("r_pl_1", "Profit & Loss Practice", "लाभ व हानि टेस्ट", 10, 10)), emptyList()),
                    TopicHierarchyItem("rrb_time_dist", "Speed, Distance & Train Problems", "चाल, दूरी एवं ट्रेन संबंधित प्रश्न", 25, listOf(TestMetaInfo("r_train_1", "Train Problems Drill", "ट्रेन प्रश्न टेस्ट", 10, 10)), emptyList()),
                    TopicHierarchyItem("rrb_mensuration", "2D & 3D Mensuration Formulas", "2D एवं 3D क्षेत्रमिति", 20, listOf(TestMetaInfo("r_mens_1", "Mensuration Drill", "क्षेत्रमिति टेस्ट", 10, 10)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("rrb_full_commercial", "Commercial Math Full Test", "व्यावसायिक गणित टेस्ट", 25, 22)
            )
        )

        val generalScienceChapters = listOf(
            ChapterHierarchyItem(
                id = "rrb_sci_physics",
                nameEn = "General Physics (Class 10 CBSE)",
                nameHi = "सामान्य भौतिक विज्ञान",
                topicsCount = 4,
                weightagePercent = 35,
                topics = listOf(
                    TopicHierarchyItem("rrb_phy_work", "Work, Power, Energy & Gravity", "कार्य, ऊर्जा, शक्ति एवं गुरुत्वाकर्षण", 25, listOf(TestMetaInfo("r_phy_1", "Physics Units & Energy", "ऊर्जा व शक्ति टेस्ट", 10, 8)), emptyList()),
                    TopicHierarchyItem("rrb_phy_optics", "Light, Reflection, Refraction & Electricity", "प्रकाश, परावर्तन, अपवर्तन एवं विद्युत", 25, listOf(TestMetaInfo("r_phy_2", "Optics & Current", "प्रकाश एवं धारा टेस्ट", 10, 8)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("rrb_full_phy", "Railway Physics Full Test", "रेलवे भौतिकी संपूर्ण टेस्ट", 20, 15)
            ),
            ChapterHierarchyItem(
                id = "rrb_sci_chemistry",
                nameEn = "General Chemistry (Class 10 CBSE)",
                nameHi = "सामान्य रसायन विज्ञान",
                topicsCount = 3,
                weightagePercent = 30,
                topics = listOf(
                    TopicHierarchyItem("rrb_chem_period", "Periodic Table & Chemical Reactions", "आवर्त सारणी एवं रासायनिक अभिक्रियाएँ", 25, listOf(TestMetaInfo("r_chem_1", "Periodic Table Drill", "आवर्त सारणी टेस्ट", 10, 8)), emptyList()),
                    TopicHierarchyItem("rrb_chem_acid", "Acids, Bases, Salts & Metals", "अम्ल, क्षार, लवण एवं धातुएँ", 25, listOf(TestMetaInfo("r_chem_2", "Acids & Metals Test", "अम्ल व धातु टेस्ट", 10, 8)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("rrb_full_chem", "Railway Chemistry Full Test", "रेलवे रसायन संपूर्ण टेस्ट", 20, 15)
            ),
            ChapterHierarchyItem(
                id = "rrb_sci_biology",
                nameEn = "Life Sciences (Class 10 CBSE)",
                nameHi = "जीव विज्ञान एवं मानव शरीर",
                topicsCount = 3,
                weightagePercent = 35,
                topics = listOf(
                    TopicHierarchyItem("rrb_bio_human", "Human Body Systems & Diseases", "मानव शरीर तंत्र एवं प्रमुख रोग", 30, listOf(TestMetaInfo("r_bio_1", "Human Systems Drill", "मानव तंत्र टेस्ट", 10, 8)), emptyList()),
                    TopicHierarchyItem("rrb_bio_plant", "Plant Physiology & Nutrition", "पादप कार्यिकी एवं पोषण", 20, listOf(TestMetaInfo("r_bio_2", "Plant Nutrition Test", "पादप पोषण टेस्ट", 10, 8)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("rrb_full_bio", "Railway Biology Full Test", "रेलवे जीवविज्ञान संपूर्ण टेस्ट", 20, 15)
            )
        )

        val reasoningChapters = listOf(
            ChapterHierarchyItem(
                id = "rrb_reas_logic",
                nameEn = "Logical & Analytical Reasoning",
                nameHi = "तार्किक एवं विश्लेषणात्मक तर्कशक्ति",
                topicsCount = 4,
                weightagePercent = 100,
                topics = listOf(
                    TopicHierarchyItem("rrb_reas_analogy", "Analogies & Classification", "सादृश्यता एवं वर्गीकरण", 25, listOf(TestMetaInfo("r_an_1", "Analogy Practice", "सादृश्यता टेस्ट", 10, 8)), emptyList()),
                    TopicHierarchyItem("rrb_reas_coding", "Coding-Decoding & Series", "कोडिंग-डिकोडिंग एवं श्रृंखला", 25, listOf(TestMetaInfo("r_cd_1", "Coding Test", "कोडिंग टेस्ट", 10, 8)), emptyList()),
                    TopicHierarchyItem("rrb_reas_venn", "Venn Diagrams & Syllogism", "वेन आरेख एवं न्याय निगमन", 25, listOf(TestMetaInfo("r_vd_1", "Venn Diagram Practice", "वेन आरेख टेस्ट", 10, 8)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("rrb_full_reas", "Railway Reasoning Full Test", "रेलवे तर्कशक्ति संपूर्ण टेस्ट", 30, 25)
            )
        )

        return ExamHierarchyItem(
            id = "railway_exams",
            nameEn = "Railways (RRB NTPC, Group D, ALP)",
            nameHi = "रेलवे भर्ती बोर्ड (RRB NTPC, ग्रुप D, ALP)",
            category = "Railway Recruitment",
            subjects = listOf(
                SubjectHierarchyItem("rrb_science", "General Science", "सामान्य विज्ञान", generalScienceChapters.size, generalScienceChapters, TestMetaInfo("r_full_sci", "Full General Science Mock", "संपूर्ण सामान्य विज्ञान मॉक", 30, 25)),
                SubjectHierarchyItem("rrb_math", "Mathematics", "गणित", mathChapters.size, mathChapters, TestMetaInfo("r_full_math", "Full Mathematics Mock", "संपूर्ण गणित मॉक", 30, 25)),
                SubjectHierarchyItem("rrb_reasoning", "General Intelligence & Reasoning", "सामान्य बुद्धिमत्ता एवं तर्कशक्ति", reasoningChapters.size, reasoningChapters, TestMetaInfo("r_full_reas_sub", "Full Reasoning Mock", "संपूर्ण तर्कशक्ति मॉक", 30, 25))
            )
        )
    }

    // =========================================================================
    // 8. UGC NET PAPER-1 HIERARCHY
    // =========================================================================
    fun getUgcNetHierarchy(): ExamHierarchyItem {
        val paper1Chapters = listOf(
            ChapterHierarchyItem(
                id = "ugc_chap_teach",
                nameEn = "Teaching & Research Aptitude",
                nameHi = "शिक्षण एवं शोध अभिक्षमता",
                topicsCount = 4,
                weightagePercent = 40,
                topics = listOf(
                    TopicHierarchyItem("ugc_teach_meth", "Teaching: Concepts & Evaluation Systems", "शिक्षण: अवधारणाएँ एवं मूल्यांकन प्रणाली", 25, listOf(TestMetaInfo("u_t_1", "Teaching Aptitude Drill", "शिक्षण अभिक्षमता टेस्ट", 10, 10)), emptyList()),
                    TopicHierarchyItem("ugc_res_meth", "Research: Types, Methods & Ethics", "शोध: प्रकार, विधियाँ एवं शोध नैतिकता", 25, listOf(TestMetaInfo("u_r_1", "Research Methodology Drill", "शोध पद्धति टेस्ट", 10, 10)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("ugc_full_tr", "Teaching & Research Full Test", "शिक्षण एवं शोध संपूर्ण टेस्ट", 25, 25)
            ),
            ChapterHierarchyItem(
                id = "ugc_chap_ict_env",
                nameEn = "ICT, People & Environment",
                nameHi = "सूचना प्रौद्योगिकी एवं पर्यावरण",
                topicsCount = 4,
                weightagePercent = 60,
                topics = listOf(
                    TopicHierarchyItem("ugc_ict_gen", "ICT: Terminology, Internet & Governance", "आईसीटी: शब्दावली, इंटरनेट एवं डिजिटल पहल", 25, listOf(TestMetaInfo("u_ict_1", "ICT Practice Test", "आईसीटी टेस्ट", 10, 10)), emptyList()),
                    TopicHierarchyItem("ugc_env_dev", "People, Development & Climate Change", "पर्यावरण, प्रदूषण एवं जलवायु परिवर्तन", 25, listOf(TestMetaInfo("u_env_1", "Environment & SDG Test", "पर्यावरण व विकास टेस्ट", 10, 10)), emptyList()),
                    TopicHierarchyItem("ugc_high_edu", "Higher Education System in India", "भारत में उच्च शिक्षा प्रणाली", 20, listOf(TestMetaInfo("u_he_1", "Higher Education Policies", "उच्च शिक्षा नीतियां", 10, 10)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("ugc_full_ict_env", "ICT & Higher Education Test", "आईसीटी व उच्च शिक्षा टेस्ट", 25, 25)
            )
        )

        return ExamHierarchyItem(
            id = "ugc_net",
            nameEn = "UGC NET Paper-1",
            nameHi = "यूजीसी नेट (सामान्य प्रश्नपत्र - 1)",
            category = "Teaching & Eligibility",
            subjects = listOf(
                SubjectHierarchyItem("ugc_paper1", "Teaching & Research Aptitude (General Paper 1)", "शिक्षण एवं शोध अभिक्षमता (पेपर 1)", paper1Chapters.size, paper1Chapters, TestMetaInfo("ugc_full_p1", "UGC NET Paper 1 Full Mock", "यूजीसी नेट पेपर 1 संपूर्ण मॉक", 50, 60))
            )
        )
    }

    // =========================================================================
    // 9. CAT HIERARCHY (MANAGEMENT ENTRANCE)
    // =========================================================================
    fun getCatHierarchy(): ExamHierarchyItem {
        val varcChapters = listOf(
            ChapterHierarchyItem(
                id = "cat_varc_rc",
                nameEn = "Reading Comprehension (Philosophy, Sociology, Tech)",
                nameHi = "रीडिंग कॉम्प्रिहेंशन (विभिन्न विषय)",
                topicsCount = 3,
                weightagePercent = 70,
                topics = listOf(
                    TopicHierarchyItem("cat_rc_long", "4-Question RC Passages Drill", "4-प्रश्न आरसी पैसेज", 20, listOf(TestMetaInfo("c_rc_1", "Philosophy & Economics RC", "अर्थशास्त्र आरसी टेस्ट", 8, 15)), emptyList()),
                    TopicHierarchyItem("cat_rc_crit", "Critical Reasoning & Author Tone", "क्रिटिकल रीजनिंग व टोन", 15, listOf(TestMetaInfo("c_rc_2", "Critical Reasoning Drill", "क्रिटिकल रीजनिंग", 8, 12)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("cat_full_rc", "CAT Full RC Test", "कैट संपूर्ण आरसी टेस्ट", 16, 25)
            ),
            ChapterHierarchyItem(
                id = "cat_varc_va",
                nameEn = "Verbal Ability (Para-Jumbles & Summary)",
                nameHi = "वर्बल एबिलिटी (पैरा समरी व जंबल्स)",
                topicsCount = 3,
                weightagePercent = 30,
                topics = listOf(
                    TopicHierarchyItem("cat_va_pj", "TITA Para-Jumbles (No Options)", "टीआईटीए पैरा जंबल्स", 20, listOf(TestMetaInfo("c_va_1", "TITA Para-Jumbles Practice", "पैरा जंबल्स टेस्ट", 5, 10)), emptyList()),
                    TopicHierarchyItem("cat_va_ps", "Para-Summary & Odd Sentence Out", "पैरा समरी एवं ऑड सेंटेंस", 15, listOf(TestMetaInfo("c_va_2", "Para Summary Drill", "पैरा समरी टेस्ट", 5, 10)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("cat_full_va", "CAT Verbal Ability Test", "कैट वर्बल एबिलिटी टेस्ट", 10, 15)
            )
        )

        val dilrChapters = listOf(
            ChapterHierarchyItem(
                id = "cat_dilr_sets",
                nameEn = "DILR High-Level Reasoning Sets",
                nameHi = "डीआईएलआर तार्किक सेट",
                topicsCount = 4,
                weightagePercent = 100,
                topics = listOf(
                    TopicHierarchyItem("cat_dilr_arr", "Games, Tournaments & Matrix Arrangements", "गेम्स, टूर्नामेंट एवं अरेंजमेंट", 20, listOf(TestMetaInfo("c_dilr_1", "Tournaments Set Practice", "टूर्नामेंट सेट टेस्ट", 5, 15)), emptyList()),
                    TopicHierarchyItem("cat_dilr_venn", "Multi-Set Venn Diagrams & Networks", "मल्टी-सेट वेन डायग्राम", 20, listOf(TestMetaInfo("c_dilr_2", "Venn Networks Set Drill", "वेन नेटवर्क्स टेस्ट", 5, 15)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("cat_full_dilr", "CAT Full DILR Sectional", "कैट डीआईएलआर सेक्शनल टेस्ट", 20, 40)
            )
        )

        val quantChapters = listOf(
            ChapterHierarchyItem(
                id = "cat_qa_arith",
                nameEn = "Arithmetic Mastery (TSD, TW, Percentages)",
                nameHi = "अंकगणित महारत",
                topicsCount = 4,
                weightagePercent = 40,
                topics = listOf(
                    TopicHierarchyItem("cat_qa_tsd", "Time, Speed, Distance, Circular Tracks", "चाल, समय, दूरी एवं वृत्तीय पथ", 25, listOf(TestMetaInfo("c_qa_1", "TSD Advanced Drill", "चाल व समय एडवांस्ड", 10, 15)), emptyList()),
                    TopicHierarchyItem("cat_qa_tw", "Time & Work, Pipes, Efficiencies", "समय व कार्य क्षमता", 20, listOf(TestMetaInfo("c_qa_2", "Work & Efficiency Test", "कार्य क्षमता टेस्ट", 10, 15)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("cat_full_arith_qa", "CAT Arithmetic Full Test", "कैट अंकगणित टेस्ट", 15, 25)
            ),
            ChapterHierarchyItem(
                id = "cat_qa_algebra",
                nameEn = "Algebra & Modern Math",
                nameHi = "बीजगणित एवं आधुनिक गणित",
                topicsCount = 4,
                weightagePercent = 60,
                topics = listOf(
                    TopicHierarchyItem("cat_qa_func", "Functions, Maxima-Minima & Logarithms", "फंक्शन्स, उच्चिष्ठ-निम्निष्ठ एवं लघुगणक", 25, listOf(TestMetaInfo("c_qa_3", "Functions & Logs Drill", "फंक्शन्स टेस्ट", 10, 15)), emptyList()),
                    TopicHierarchyItem("cat_qa_geom", "Geometry, Circles, Coordinate & Trigonometry", "ज्यामिति, वृत्त एवं निर्देशांक", 25, listOf(TestMetaInfo("c_qa_4", "CAT Geometry Drill", "कैट ज्यामिति टेस्ट", 10, 15)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("cat_full_alg_geom", "CAT Algebra & Geometry Test", "कैट बीजगणित व ज्यामिति टेस्ट", 15, 25)
            )
        )

        return ExamHierarchyItem(
            id = "cat_exam",
            nameEn = "CAT (Common Admission Test)",
            nameHi = "कैट (प्रबंधन प्रवेश परीक्षा)",
            category = "Management Entrance",
            subjects = listOf(
                SubjectHierarchyItem("cat_varc", "Verbal Ability & Reading Comprehension (VARC)", "वर्बल एबिलिटी एवं आरसी", varcChapters.size, varcChapters, TestMetaInfo("c_full_varc", "Full VARC Sectional Mock", "पूर्ण वीएआरसी सेक्शनल मॉक", 24, 40)),
                SubjectHierarchyItem("cat_dilr", "Data Interpretation & Logical Reasoning (DILR)", "डेटा इंटरप्रिटेशन एवं एलआर", dilrChapters.size, dilrChapters, TestMetaInfo("c_full_dilr_sub", "Full DILR Sectional Mock", "पूर्ण डीआईएलआर सेक्शनल मॉक", 20, 40)),
                SubjectHierarchyItem("cat_qa", "Quantitative Ability (QA)", "मात्रात्मक योग्यता (गणित)", quantChapters.size, quantChapters, TestMetaInfo("c_full_qa", "Full QA Sectional Mock", "पूर्ण क्यूए सेक्शनल मॉक", 22, 40))
            )
        )
    }

    // =========================================================================
    // 10. CUET UG HIERARCHY (CENTRAL UNIVERSITIES ENTRANCE)
    // =========================================================================
    fun getCuetHierarchy(): ExamHierarchyItem {
        val generalTestChapters = listOf(
            ChapterHierarchyItem(
                id = "cuet_gt_quant_reas",
                nameEn = "General Mental Ability & Quantitative Reasoning",
                nameHi = "सामान्य मानसिक योग्यता एवं संख्यात्मक अभिरुचि",
                topicsCount = 4,
                weightagePercent = 60,
                topics = listOf(
                    TopicHierarchyItem("cuet_gt_math", "Basic Arithmetic & Percentages", "आधारभूत अंकगणित एवं प्रतिशत", 25, listOf(TestMetaInfo("cu_m_1", "CUET General Math", "सीयूईटी गणित अभ्यास", 10, 10)), emptyList()),
                    TopicHierarchyItem("cuet_gt_reas", "Logical, Analytical & Visual Reasoning", "तार्किक एवं दृश्यात्मक तर्कशक्ति", 25, listOf(TestMetaInfo("cu_r_1", "CUET Reasoning Drill", "सीयूईटी तर्कशक्ति टेस्ट", 10, 10)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("cuet_full_gt_mr", "General Test Mental Ability Full", "सामान्य मानसिक योग्यता टेस्ट", 25, 25)
            ),
            ChapterHierarchyItem(
                id = "cuet_gt_gk",
                nameEn = "General Knowledge & Current Affairs",
                nameHi = "सामान्य ज्ञान एवं समसामयिकी",
                topicsCount = 3,
                weightagePercent = 40,
                topics = listOf(
                    TopicHierarchyItem("cuet_gt_cur", "National & International Events", "राष्ट्रीय एवं अंतर्राष्ट्रीय घटनाक्रम", 25, listOf(TestMetaInfo("cu_gk_1", "Current Affairs Drill", "करेंट अफेयर्स टेस्ट", 10, 8)), emptyList())
                ),
                fullChapterTest = TestMetaInfo("cuet_full_gt_gk", "General Knowledge Full Test", "सामान्य ज्ञान संपूर्ण टेस्ट", 25, 20)
            )
        )

        return ExamHierarchyItem(
            id = "cuet_ug",
            nameEn = "CUET UG",
            nameHi = "सीयूईटी यूजी (विश्वविद्यालय प्रवेश)",
            category = "University Entrance",
            subjects = listOf(
                SubjectHierarchyItem("cuet_gen_test", "Section III: General Test", "सामान्य परीक्षण (जनरल टेस्ट)", generalTestChapters.size, generalTestChapters, TestMetaInfo("cuet_full_gt", "CUET General Test Full Mock", "सीयूईटी जनरल टेस्ट पूर्ण मॉक", 60, 60))
            )
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

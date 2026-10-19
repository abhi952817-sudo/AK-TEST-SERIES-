package Com.ak.testseries.exam

/**
 * Universal Exam Registry containing configuration for 20+ major Indian competitive exams.
 * Enriched with authentic difficulty ratios (Easy/Medium/Hard), supported QuestionTypes,
 * exam-specific AI system instructions, and pattern rules.
 * Dynamically configurable without rewriting code.
 */
object UniversalExamRegistry {

    private val defaultExams = mutableMapOf<String, ExamConfig>()

    init {
        registerDefaultExams()
    }

    private fun registerDefaultExams() {
        // 1. NEET UG (Medical Entrance)
        register(
            ExamConfig(
                examId = "neet_ug",
                examName = "NEET UG",
                shortName = "NEET",
                category = "Medical Entrance",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 45, mediumPercent = 40, hardPercent = 15),
                supportedQuestionTypes = listOf(
                    QuestionType.DIRECT_MCQ,
                    QuestionType.STATEMENT_I_II,
                    QuestionType.ASSERTION_REASON,
                    QuestionType.MATCH_LIST_I_II,
                    QuestionType.DIAGRAM_LABELING,
                    QuestionType.GRAPH
                ),
                aiSystemInstruction = "You are the Chief Academic Examiner for NEET UG. Strictly adhere to NCERT Class 11 & 12 curriculum for Physics, Chemistry, and Biology (Botany & Zoology). Emphasize NCERT factual accuracy, biochemical cycles, diagrammatic interpretations, genetics crosses, and conceptual numericals. Eliminate out-of-syllabus graduate concepts.",
                patternRules = listOf(
                    "+4 marks for correct answer, -1 mark penalty for incorrect answer",
                    "Section A: 35 compulsory questions per subject",
                    "Section B: 15 questions per subject (students attempt any 10)",
                    "Total 720 marks (180 questions attempted out of 200)",
                    "Duration 200 minutes (3 hours 20 minutes)",
                    "Strict bilingual formatting in English and Hindi"
                ),
                stages = listOf(
                    StageConfig(
                        stageId = "neet_main",
                        stageName = "Main Exam",
                        papers = listOf(
                            PaperConfig(
                                paperId = "neet_paper_1",
                                paperName = "NEET Single Paper",
                                durationMinutes = 200,
                                totalMarks = 720.0,
                                subjects = listOf(
                                    SubjectConfig(
                                        "physics", "Physics",
                                        listOf(
                                            SectionConfig("phy_sec_a", "Section A", 35, 35, 4.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("phy_sec_b", "Section B", 15, 10, 4.0, 1.0, QuestionType.MCQ_SINGLE)
                                        )
                                    ),
                                    SubjectConfig(
                                        "chemistry", "Chemistry",
                                        listOf(
                                            SectionConfig("chem_sec_a", "Section A", 35, 35, 4.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("chem_sec_b", "Section B", 15, 10, 4.0, 1.0, QuestionType.MCQ_SINGLE)
                                        )
                                    ),
                                    SubjectConfig(
                                        "botany", "Botany",
                                        listOf(
                                            SectionConfig("bot_sec_a", "Section A", 35, 35, 4.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("bot_sec_b", "Section B", 15, 10, 4.0, 1.0, QuestionType.MCQ_SINGLE)
                                        )
                                    ),
                                    SubjectConfig(
                                        "zoology", "Zoology",
                                        listOf(
                                            SectionConfig("zoo_sec_a", "Section A", 35, 35, 4.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("zoo_sec_b", "Section B", 15, 10, 4.0, 1.0, QuestionType.MCQ_SINGLE)
                            )
                        )
                    )
                )
            )
        )
                )
            )
        )

        // 2. JEE Main (Engineering Entrance)
        register(
            ExamConfig(
                examId = "jee_main",
                examName = "JEE Main",
                shortName = "JEE",
                category = "Engineering Entrance",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 25, mediumPercent = 50, hardPercent = 25),
                supportedQuestionTypes = listOf(
                    QuestionType.DIRECT_MCQ,
                    QuestionType.NUMERICAL_VALUE,
                    QuestionType.ASSERTION_REASON,
                    QuestionType.MULTI_STATEMENT,
                    QuestionType.GRAPH
                ),
                aiSystemInstruction = "You are the Chief Academic Examiner for JEE Main. Generate rigorous analytical questions for Physics, Chemistry, and Mathematics. Focus on multi-concept synthesis, calculus-based derivations, vector algebra, organic reaction mechanisms, and physical chemistry stoichiometry. Numerical value questions require exact integer or rounded values.",
                patternRules = listOf(
                    "+4 marks for correct, -1 mark penalty for incorrect",
                    "Section A: 20 Multiple Choice Questions (compulsory)",
                    "Section B: 10 Numerical value questions (attempt any 5)",
                    "Total 300 marks (75 questions attempted out of 90)",
                    "Duration 180 minutes (3 hours)"
                ),
                stages = listOf(
                    StageConfig(
                        "jee_paper_1", "B.E./B.Tech Paper 1",
                        listOf(
                            PaperConfig(
                                "jee_main_p1", "Paper 1",
                                durationMinutes = 180,
                                totalMarks = 300.0,
                                subjects = listOf(
                                    SubjectConfig(
                                        "physics", "Physics",
                                        listOf(
                                            SectionConfig("phy_sec_a", "Section A (MCQ)", 20, 20, 4.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("phy_sec_b", "Section B (NVQ)", 10, 5, 4.0, 1.0, QuestionType.NUMERICAL_VALUE, optionsCount = 0)
                                        )
                                    ),
                                    SubjectConfig(
                                        "chemistry", "Chemistry",
                                        listOf(
                                            SectionConfig("chem_sec_a", "Section A (MCQ)", 20, 20, 4.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("chem_sec_b", "Section B (NVQ)", 10, 5, 4.0, 1.0, QuestionType.NUMERICAL_VALUE, optionsCount = 0)
                                        )
                                    ),
                                    SubjectConfig(
                                        "mathematics", "Mathematics",
                                        listOf(
                                            SectionConfig("math_sec_a", "Section A (MCQ)", 20, 20, 4.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("math_sec_b", "Section B (NVQ)", 10, 5, 4.0, 1.0, QuestionType.NUMERICAL_VALUE, optionsCount = 0)
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // 2b. JEE Advanced (Joint Entrance Examination - Advanced)
        register(
            ExamConfig(
                examId = "jee_advanced",
                examName = "JEE Advanced",
                shortName = "JEE Adv",
                category = "Engineering Entrance",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 10, mediumPercent = 45, hardPercent = 45),
                supportedQuestionTypes = listOf(
                    QuestionType.MCQ_SINGLE,
                    QuestionType.MCQ_MULTIPLE,
                    QuestionType.NUMERICAL_VALUE,
                    QuestionType.MATCH_LIST_I_II
                ),
                aiSystemInstruction = "You are the Chief Academic Examiner for JEE Advanced (IITs). Generate exceptionally rigorous, multi-concept analytical problems in Physics, Chemistry, and Mathematics. Include Single Correct (+3/-1), One or More Than One Correct Multiple Choice (+4/-2), and Non-Negative Numerical Answer Type (+3/0) problems requiring high precision.",
                patternRules = listOf(
                    "Section 1 (MCQ Single): +3 marks for correct, -1 mark penalty for incorrect",
                    "Section 2 (Multiple Correct): +4 marks for correct, -2 marks penalty for incorrect",
                    "Section 3 (Numerical Value): +3 marks for correct, 0 penalty for incorrect",
                    "54 questions total (18 Physics, 18 Chemistry, 18 Mathematics), 180 marks, Duration 180 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "jee_adv_paper_1", "JEE Advanced Paper 1",
                        listOf(
                            PaperConfig(
                                "jee_adv_p1", "Paper 1",
                                durationMinutes = 180,
                                totalMarks = 180.0,
                                subjects = listOf(
                                    SubjectConfig(
                                        "physics", "Physics",
                                        listOf(
                                            SectionConfig("phy_sec_single", "Section 1 (Single Correct)", 4, 4, 3.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("phy_sec_multi", "Section 2 (Multiple Correct)", 6, 6, 4.0, 2.0, QuestionType.MCQ_MULTIPLE),
                                            SectionConfig("phy_sec_num", "Section 3 (Numerical Value)", 8, 8, 3.0, 0.0, QuestionType.NUMERICAL_VALUE, optionsCount = 0)
                                        )
                                    ),
                                    SubjectConfig(
                                        "chemistry", "Chemistry",
                                        listOf(
                                            SectionConfig("chem_sec_single", "Section 1 (Single Correct)", 4, 4, 3.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("chem_sec_multi", "Section 2 (Multiple Correct)", 6, 6, 4.0, 2.0, QuestionType.MCQ_MULTIPLE),
                                            SectionConfig("chem_sec_num", "Section 3 (Numerical Value)", 8, 8, 3.0, 0.0, QuestionType.NUMERICAL_VALUE, optionsCount = 0)
                                        )
                                    ),
                                    SubjectConfig(
                                        "mathematics", "Mathematics",
                                        listOf(
                                            SectionConfig("math_sec_single", "Section 1 (Single Correct)", 4, 4, 3.0, 1.0, QuestionType.MCQ_SINGLE),
                                            SectionConfig("math_sec_multi", "Section 2 (Multiple Correct)", 6, 6, 4.0, 2.0, QuestionType.MCQ_MULTIPLE),
                                            SectionConfig("math_sec_num", "Section 3 (Numerical Value)", 8, 8, 3.0, 0.0, QuestionType.NUMERICAL_VALUE, optionsCount = 0)
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        // 3. CUET UG (Common University Entrance Test)
        register(
            ExamConfig(
                examId = "cuet_ug",
                examName = "CUET UG",
                shortName = "CUET",
                category = "University Entrance",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 50, mediumPercent = 40, hardPercent = 10),
                supportedQuestionTypes = listOf(
                    QuestionType.DIRECT_MCQ,
                    QuestionType.MATCH_LIST_I_II,
                    QuestionType.PASSAGE_BASED,
                    QuestionType.STATEMENT_I_II
                ),
                aiSystemInstruction = "You are the Academic Specialist for CUET UG. Focus strictly on NCERT Class 12 domain syllabus and General Test logic. Formulate clear, syllabus-bound MCQs, matching tables, and comprehension passages. Avoid graduate-level complexities.",
                patternRules = listOf(
                    "+5 marks for correct, -1 mark penalty for incorrect",
                    "Domain subjects: 50 questions total, attempt any 40",
                    "Duration 45 to 60 minutes per domain test",
                    "Bilingual support in English and Hindi"
                ),
                stages = listOf(
                    StageConfig(
                        "cuet_domain", "Domain & General Test",
                        listOf(
                            PaperConfig(
                                "cuet_domain_paper", "Domain Paper",
                                durationMinutes = 45,
                                totalMarks = 200.0,
                                subjects = listOf(
                                    SubjectConfig("domain_subj", "Domain Subject", listOf(SectionConfig("domain_sec", "Domain MCQ", 50, 40, 5.0, 1.0, QuestionType.MCQ_SINGLE)))
                    )
                )
            )
        )
                )
            )
        )

        // 4. UPSC CSE (Civil Services Prelims)
        register(
            ExamConfig(
                examId = "upsc_cse",
                examName = "UPSC Civil Services",
                shortName = "UPSC",
                category = "Civil Services",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 20, mediumPercent = 50, hardPercent = 30),
                supportedQuestionTypes = listOf(
                    QuestionType.MULTI_STATEMENT,
                    QuestionType.STATEMENT_I_II,
                    QuestionType.MATCH_LIST_I_II,
                    QuestionType.ASSERTION_REASON,
                    QuestionType.DIRECT_MCQ
                ),
                aiSystemInstruction = "You are the Senior Question Architect for UPSC Civil Services Prelims (General Studies Paper 1). Frame nuanced multi-statement questions ('Which of the statements given above is/are correct?', 'How many pairs are correctly matched?'). Require deep interdisciplinary understanding of Indian Polity, Constitution, Macroeconomics, Modern History, Ecology, Biodiversity, and Science & Technology.",
                patternRules = listOf(
                    "+2.0 marks for correct answer, -0.66 (1/3rd) penalty for incorrect answer",
                    "100 multiple choice questions, total 200 marks",
                    "Duration 120 minutes (2 hours)",
                    "Strict bilingual formatting (English & Hindi)"
                ),
                stages = listOf(
                    StageConfig(
                        "prelims", "Civil Services Preliminary",
                        listOf(
                            PaperConfig(
                                "gs_paper_1", "General Studies Paper 1",
                                durationMinutes = 120,
                                totalMarks = 200.0,
                                subjects = listOf(
                                    SubjectConfig("polity", "Indian Polity & Governance", listOf(SectionConfig("polity_sec", "Polity", 20, 20, 2.0, 0.66, QuestionType.STATEMENT_BASED))),
                                    SubjectConfig("economy", "Economic & Social Development", listOf(SectionConfig("econ_sec", "Economy", 20, 20, 2.0, 0.66, QuestionType.STATEMENT_BASED))),
                                    SubjectConfig("history", "History of India & Art & Culture", listOf(SectionConfig("hist_sec", "History", 20, 20, 2.0, 0.66, QuestionType.STATEMENT_BASED))),
                                    SubjectConfig("geography_env", "Geography & Environment Ecology", listOf(SectionConfig("env_sec", "Environment", 25, 25, 2.0, 0.66, QuestionType.STATEMENT_BASED))),
                                    SubjectConfig("science_tech", "General Science & Technology", listOf(SectionConfig("sci_sec", "Science", 15, 15, 2.0, 0.66, QuestionType.STATEMENT_BASED)))
                    )
                )
            )
        )
                )
            )
        )

        // 5. SSC CGL (Staff Selection Commission Combined Graduate Level)
        register(
            ExamConfig(
                examId = "ssc_cgl",
                examName = "SSC CGL",
                shortName = "CGL",
                category = "SSC",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 35, mediumPercent = 50, hardPercent = 15),
                supportedQuestionTypes = listOf(
                    QuestionType.DIRECT_MCQ,
                    QuestionType.STATEMENT_I_II,
                    QuestionType.GRAPH,
                    QuestionType.DIAGRAM_LABELING
                ),
                aiSystemInstruction = "You are an examiner for SSC CGL Tier 1. Test speed, mental arithmetic, geometry, trigonometry, algebra, error detection in English, reading comprehension, reasoning patterns, and static GK/Current Affairs. Questions must be crisp, time-sensitive, and free of ambiguous phrasing.",
                patternRules = listOf(
                    "+2 marks for correct answer, -0.50 mark penalty for incorrect answer",
                    "100 questions (25 Reasoning, 25 General Awareness, 25 Quantitative Aptitude, 25 English)",
                    "Total 200 marks, Duration 60 minutes",
                    "Sectional cutoff does not apply in Tier 1"
                ),
                stages = listOf(
                    StageConfig(
                        "tier_1", "Tier 1 (Preliminary)",
                        listOf(
                            PaperConfig(
                                "cgl_tier1_paper", "Tier 1 Comprehensive",
                                durationMinutes = 60,
                                totalMarks = 200.0,
                                subjects = listOf(
                                    SubjectConfig("reasoning", "General Intelligence & Reasoning", listOf(SectionConfig("gi_sec", "Reasoning", 25, 25, 2.0, 0.50, QuestionType.MCQ_SINGLE))),
                                    SubjectConfig("general_awareness", "General Awareness", listOf(SectionConfig("ga_sec", "GA", 25, 25, 2.0, 0.50, QuestionType.MCQ_SINGLE))),
                                    SubjectConfig("quantitative_aptitude", "Quantitative Aptitude", listOf(SectionConfig("qa_sec", "Quant", 25, 25, 2.0, 0.50, QuestionType.MCQ_SINGLE))),
                                    SubjectConfig("english_comprehension", "English Comprehension", listOf(SectionConfig("eng_sec", "English", 25, 25, 2.0, 0.50, QuestionType.MCQ_SINGLE)))
                    )
                )
            )
        )
                )
            )
        )

        // 6. SSC CHSL (Combined Higher Secondary Level)
        register(
            ExamConfig(
                examId = "ssc_chsl",
                examName = "SSC CHSL (10+2)",
                shortName = "CHSL",
                category = "SSC",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 45, mediumPercent = 45, hardPercent = 10),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.GRAPH),
                aiSystemInstruction = "You are an examiner for SSC CHSL (10+2 Level). Formulate balanced questions in quantitative aptitude (basic arithmetic and geometry), logical reasoning, English grammar & vocabulary, and general awareness.",
                patternRules = listOf(
                    "+2 marks for correct, -0.50 mark penalty for incorrect",
                    "100 questions, 200 total marks, Duration 60 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "tier_1", "Tier 1",
                        listOf(
                            PaperConfig(
                                "chsl_tier1_paper", "Tier 1 Paper",
                                durationMinutes = 60,
                                totalMarks = 200.0,
                                subjects = listOf(
                                    SubjectConfig("reasoning", "Reasoning", listOf(SectionConfig("gi_sec", "Reasoning", 25, 25, 2.0, 0.50))),
                                    SubjectConfig("general_awareness", "General Awareness", listOf(SectionConfig("ga_sec", "GA", 25, 25, 2.0, 0.50))),
                                    SubjectConfig("quantitative_aptitude", "Quant Aptitude", listOf(SectionConfig("qa_sec", "Quant", 25, 25, 2.0, 0.50))),
                                    SubjectConfig("english", "English Language", listOf(SectionConfig("eng_sec", "English", 25, 25, 2.0, 0.50)))
                    )
                )
            )
        )
                )
            )
        )

        // 7. SSC MTS & Havaldar
        register(
            ExamConfig(
                examId = "ssc_mts",
                examName = "SSC MTS & Havaldar",
                shortName = "MTS",
                category = "SSC",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 60, mediumPercent = 35, hardPercent = 5),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ),
                aiSystemInstruction = "You are an examiner for SSC MTS (Matriculation Level). Test foundational numerical abilities, basic reasoning, core general knowledge (history, geography, science), and elementary English grammar.",
                patternRules = listOf(
                    "Session 1: +3 marks per question, NO negative marking",
                    "Session 2: +3 marks per question, -1 mark penalty for incorrect",
                    "Duration 90 minutes total (45 minutes per session)"
                ),
                stages = listOf(
                    StageConfig(
                        "session_1_2", "Session 1 & 2",
                        listOf(
                            PaperConfig(
                                "mts_paper", "MTS CBT",
                                durationMinutes = 90,
                                totalMarks = 270.0,
                                subjects = listOf(
                                    SubjectConfig("session_1_math", "Numerical Ability (No Neg)", listOf(SectionConfig("math_sec", "Math", 20, 20, 3.0, 0.0))),
                                    SubjectConfig("session_1_reasoning", "Reasoning (No Neg)", listOf(SectionConfig("reaz_sec", "Reasoning", 20, 20, 3.0, 0.0))),
                                    SubjectConfig("session_2_ga", "General Awareness", listOf(SectionConfig("ga_sec", "GA", 25, 25, 3.0, 1.0))),
                                    SubjectConfig("session_2_eng", "English Language", listOf(SectionConfig("eng_sec", "English", 25, 25, 3.0, 1.0)))
                    )
                )
            )
        )
                )
            )
        )

        // 8. SSC GD Constable
        register(
            ExamConfig(
                examId = "ssc_gd",
                examName = "SSC GD Constable",
                shortName = "GD",
                category = "SSC",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 60, mediumPercent = 35, hardPercent = 5),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ),
                aiSystemInstruction = "You are an examiner for SSC GD Constable. Craft practical, high-clarity questions testing basic intelligence, elementary math, general knowledge, and language comprehension for constable recruits.",
                patternRules = listOf(
                    "+2 marks for correct, -0.25 mark penalty for incorrect",
                    "80 questions (20 each in Reasoning, GK, Math, Hindi/English)",
                    "Total 160 marks, Duration 60 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "cbe", "Computer Based Exam",
                        listOf(
                            PaperConfig(
                                "gd_cbt", "GD CBT",
                                durationMinutes = 60,
                                totalMarks = 160.0,
                                subjects = listOf(
                                    SubjectConfig("reasoning", "General Intelligence", listOf(SectionConfig("gi_sec", "Reasoning", 20, 20, 2.0, 0.25))),
                                    SubjectConfig("general_knowledge", "General Knowledge", listOf(SectionConfig("gk_sec", "GK", 20, 20, 2.0, 0.25))),
                                    SubjectConfig("elementary_math", "Elementary Mathematics", listOf(SectionConfig("math_sec", "Math", 20, 20, 2.0, 0.25))),
                                    SubjectConfig("language", "English/Hindi", listOf(SectionConfig("lang_sec", "Language", 20, 20, 2.0, 0.25)))
                    )
                )
            )
        )
                )
            )
        )

        // 9. SSC CPO (Central Police Organization)
        register(
            ExamConfig(
                examId = "ssc_cpo",
                examName = "SSC CPO (Sub-Inspector)",
                shortName = "CPO",
                category = "SSC",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 35, mediumPercent = 50, hardPercent = 15),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ),
                aiSystemInstruction = "You are an examiner for SSC CPO. Generate comprehensive questions testing analytical reasoning, General Knowledge/Science, quantitative arithmetic & algebra, and English language comprehension.",
                patternRules = listOf(
                    "+1 mark for correct, -0.25 mark penalty for incorrect",
                    "200 questions (50 each in 4 sections), Total 200 marks, Duration 120 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "paper_1", "Paper 1",
                        listOf(
                            PaperConfig(
                                "cpo_p1", "Paper 1",
                                durationMinutes = 120,
                                totalMarks = 200.0,
                                subjects = listOf(
                                    SubjectConfig("reasoning", "Reasoning", listOf(SectionConfig("gi_sec", "Reasoning", 50, 50, 1.0, 0.25))),
                                    SubjectConfig("general_knowledge", "General Knowledge", listOf(SectionConfig("gk_sec", "GK", 50, 50, 1.0, 0.25))),
                                    SubjectConfig("quantitative_aptitude", "Quant", listOf(SectionConfig("qa_sec", "Quant", 50, 50, 1.0, 0.25))),
                                    SubjectConfig("english_comprehension", "English", listOf(SectionConfig("eng_sec", "English", 50, 50, 1.0, 0.25)))
                    )
                )
            )
        )
                )
            )
        )

        // 10. RRB NTPC (Non-Technical Popular Categories)
        register(
            ExamConfig(
                examId = "rrb_ntpc",
                examName = "RRB NTPC",
                shortName = "NTPC",
                category = "Railways",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 40, mediumPercent = 45, hardPercent = 15),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.TABLE_BASED),
                aiSystemInstruction = "You are an examiner for RRB NTPC CBT-1 & CBT-2. Prioritize General Awareness (Indian Railways history, science, geography, constitution), Mathematics (percentages, ratio, time & work), and General Intelligence.",
                patternRules = listOf(
                    "+1 mark for correct, -0.33 (1/3rd) penalty for incorrect",
                    "100 questions (40 GA, 30 Math, 30 Reasoning), Total 100 marks, Duration 90 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "cbt_1", "1st Stage CBT",
                        listOf(
                            PaperConfig(
                                "ntpc_cbt1", "CBT 1",
                                durationMinutes = 90,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("general_awareness", "General Awareness", listOf(SectionConfig("ga_sec", "GA", 40, 40, 1.0, 0.33))),
                                    SubjectConfig("mathematics", "Mathematics", listOf(SectionConfig("math_sec", "Math", 30, 30, 1.0, 0.33))),
                                    SubjectConfig("general_intelligence", "Reasoning", listOf(SectionConfig("gi_sec", "Reasoning", 30, 30, 1.0, 0.33)))
                    )
                )
            )
        )
                )
            )
        )

        // 11. RRB Group D
        register(
            ExamConfig(
                examId = "rrb_group_d",
                examName = "RRB Group D",
                shortName = "Group D",
                category = "Railways",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 50, mediumPercent = 40, hardPercent = 10),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ),
                aiSystemInstruction = "You are an examiner for Railway RRB Group D (Level 1). Prioritize General Science (Class 10 CBSE physics, chemistry, biology), Basic Mathematics, General Intelligence, and Current Affairs.",
                patternRules = listOf(
                    "+1 mark for correct, -0.33 (1/3rd) penalty for incorrect",
                    "100 questions (25 Science, 25 Math, 30 Reasoning, 20 GA/Current Affairs), Total 100 marks, 90 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "cbt", "CBT Exam",
                        listOf(
                            PaperConfig(
                                "group_d_paper", "CBT Paper",
                                durationMinutes = 90,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("general_science", "General Science", listOf(SectionConfig("sci_sec", "Science", 25, 25, 1.0, 0.33))),
                                    SubjectConfig("mathematics", "Mathematics", listOf(SectionConfig("math_sec", "Math", 25, 25, 1.0, 0.33))),
                                    SubjectConfig("general_intelligence", "Reasoning", listOf(SectionConfig("gi_sec", "Reasoning", 30, 30, 1.0, 0.33))),
                                    SubjectConfig("general_awareness", "Current Affairs", listOf(SectionConfig("ga_sec", "GA", 20, 20, 1.0, 0.33)))
                    )
                )
            )
        )
                )
            )
        )

        // 12. RRB ALP (Assistant Loco Pilot)
        register(
            ExamConfig(
                examId = "rrb_alp",
                examName = "RRB ALP",
                shortName = "ALP",
                category = "Railways",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 35, mediumPercent = 50, hardPercent = 15),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.NUMERICAL_VALUE, QuestionType.DIAGRAM_LABELING),
                aiSystemInstruction = "You are an examiner for RRB ALP CBT-1. Focus on Basic Science & Engineering (Units, Measurements, Work Energy & Power, Heat, Electricity, Levers), Technical Mathematics, and General Intelligence.",
                patternRules = listOf(
                    "+1 mark for correct, -0.33 (1/3rd) penalty for incorrect",
                    "75 questions, Total 75 marks, Duration 60 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "cbt_1", "1st Stage CBT",
                        listOf(
                            PaperConfig(
                                "alp_cbt1", "CBT 1",
                                durationMinutes = 60,
                                totalMarks = 75.0,
                                subjects = listOf(
                                    SubjectConfig("mathematics", "Mathematics", listOf(SectionConfig("math_sec", "Math", 20, 20, 1.0, 0.33))),
                                    SubjectConfig("mental_ability", "Reasoning", listOf(SectionConfig("reason_sec", "Reasoning", 25, 25, 1.0, 0.33))),
                                    SubjectConfig("general_science", "General Science", listOf(SectionConfig("sci_sec", "Science", 20, 20, 1.0, 0.33))),
                                    SubjectConfig("general_awareness", "General Awareness", listOf(SectionConfig("ga_sec", "GA", 10, 10, 1.0, 0.33)))
                    )
                )
            )
        )
                )
            )
        )

        // 13. RRB Technician
        register(
            ExamConfig(
                examId = "rrb_technician",
                examName = "RRB Technician",
                shortName = "Technician",
                category = "Railways",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 40, mediumPercent = 45, hardPercent = 15),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.NUMERICAL_VALUE),
                aiSystemInstruction = "You are an examiner for RRB Technician. Emphasize Basic Science & Engineering applications, technical trade fundamentals, Mathematics, and General Awareness.",
                patternRules = listOf(
                    "+1 mark for correct, -0.33 (1/3rd) penalty for incorrect",
                    "100 questions, Total 100 marks, Duration 90 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "cbt", "CBT Examination",
                        listOf(
                            PaperConfig(
                                "tech_paper", "CBT Paper",
                                durationMinutes = 90,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("mathematics", "Mathematics", listOf(SectionConfig("math_sec", "Math", 25, 25, 1.0, 0.33))),
                                    SubjectConfig("general_intelligence", "General Intelligence", listOf(SectionConfig("gi_sec", "Reasoning", 25, 25, 1.0, 0.33))),
                                    SubjectConfig("general_science", "General Science", listOf(SectionConfig("sci_sec", "Science", 40, 40, 1.0, 0.33))),
                                    SubjectConfig("general_awareness", "General Awareness", listOf(SectionConfig("ga_sec", "GA", 10, 10, 1.0, 0.33)))
                    )
                )
            )
        )
                )
            )
        )

        // 14. RRB JE (Junior Engineer)
        register(
            ExamConfig(
                examId = "rrb_je",
                examName = "RRB JE",
                shortName = "RRB JE",
                category = "Railways",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 30, mediumPercent = 50, hardPercent = 20),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.NUMERICAL_VALUE, QuestionType.DIAGRAM_LABELING),
                aiSystemInstruction = "You are an examiner for RRB Junior Engineer (CBT 1). Test Mathematics (algebra, calculus, trigonometry), General Science, General Intelligence, and General Awareness for diploma/degree engineering candidates.",
                patternRules = listOf(
                    "+1 mark for correct, -0.33 (1/3rd) penalty for incorrect",
                    "100 questions, Total 100 marks, Duration 90 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "cbt_1", "Stage 1 CBT",
                        listOf(
                            PaperConfig(
                                "je_cbt1", "CBT 1",
                                durationMinutes = 90,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("mathematics", "Mathematics", listOf(SectionConfig("math_sec", "Math", 30, 30, 1.0, 0.33))),
                                    SubjectConfig("general_intelligence", "General Intelligence", listOf(SectionConfig("gi_sec", "Reasoning", 25, 25, 1.0, 0.33))),
                                    SubjectConfig("general_awareness", "General Awareness", listOf(SectionConfig("ga_sec", "GA", 15, 15, 1.0, 0.33))),
                                    SubjectConfig("general_science", "General Science", listOf(SectionConfig("sci_sec", "Science", 30, 30, 1.0, 0.33)))
                    )
                )
            )
        )
                )
            )
        )

        // 15. CTET (Central Teacher Eligibility Test)
        register(
            ExamConfig(
                examId = "ctet",
                examName = "CTET",
                shortName = "CTET",
                category = "Teaching",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 50, mediumPercent = 40, hardPercent = 10),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.CASE_BASED, QuestionType.STATEMENT_I_II),
                aiSystemInstruction = "You are an examiner for CTET Paper 1 & 2. Formulate pedagogy-centric questions on Child Development and Pedagogy (CDP), inclusive education, learning theories of Piaget, Vygotsky, and Kohlberg, language learning pedagogy, and pedagogical content knowledge.",
                patternRules = listOf(
                    "+1 mark for correct, NO negative marking (0 penalty)",
                    "150 questions total, 150 marks, Duration 150 minutes (2.5 hours)"
                ),
                stages = listOf(
                    StageConfig(
                        "paper_1", "Paper 1 (Primary Stage I-V)",
                        listOf(
                            PaperConfig(
                                "ctet_p1", "Paper 1",
                                durationMinutes = 150,
                                totalMarks = 150.0,
                                subjects = listOf(
                                    SubjectConfig("cdp", "Child Development & Pedagogy", listOf(SectionConfig("cdp_sec", "CDP", 30, 30, 1.0, 0.0))),
                                    SubjectConfig("language_1", "Language I (Compulsory)", listOf(SectionConfig("l1_sec", "Language I", 30, 30, 1.0, 0.0))),
                                    SubjectConfig("language_2", "Language II (Compulsory)", listOf(SectionConfig("l2_sec", "Language II", 30, 30, 1.0, 0.0))),
                                    SubjectConfig("mathematics", "Mathematics & Pedagogy", listOf(SectionConfig("math_sec", "Mathematics", 30, 30, 1.0, 0.0))),
                                    SubjectConfig("environmental_studies", "Environmental Studies (EVS)", listOf(SectionConfig("evs_sec", "EVS", 30, 30, 1.0, 0.0)))
                    )
                )
            )
        )
                )
            )
        )

        // 16. UGC NET (University Grants Commission National Eligibility Test)
        register(
            ExamConfig(
                examId = "ugc_net",
                examName = "UGC NET",
                shortName = "UGC NET",
                category = "Teaching",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 25, mediumPercent = 50, hardPercent = 25),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.ASSERTION_REASON, QuestionType.MATCH_LIST_I_II, QuestionType.STATEMENT_I_II),
                aiSystemInstruction = "You are an examiner for UGC NET Paper 1. Focus on Teaching Aptitude, Research Aptitude & Methodology, Communication, Mathematical Reasoning & Aptitude, Logical Reasoning, Data Interpretation, Information & Communication Technology (ICT), and Higher Education System in India.",
                patternRules = listOf(
                    "+2 marks for correct, NO negative marking (0 penalty)",
                    "Paper 1: 50 questions (100 marks), Composite duration 180 minutes with Paper 2"
                ),
                stages = listOf(
                    StageConfig(
                        "paper_1_2", "Paper 1 (Common General Paper)",
                        listOf(
                            PaperConfig(
                                "net_p1", "General Paper on Teaching & Research",
                                durationMinutes = 60,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("teaching_aptitude", "Teaching & Research Aptitude", listOf(SectionConfig("ta_sec", "Teaching & Research", 50, 50, 2.0, 0.0)))
                    )
                )
            )
        )
                )
            )
        )

        // 17. CAT (Common Admission Test - IIMs)
        register(
            ExamConfig(
                examId = "cat",
                examName = "CAT",
                shortName = "CAT",
                category = "Management",
                language = "English",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 15, mediumPercent = 50, hardPercent = 35),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.NUMERICAL_VALUE, QuestionType.TITA, QuestionType.PASSAGE_BASED, QuestionType.CASE_BASED),
                aiSystemInstruction = "You are the Chief Academic Examiner for CAT (IIMs). Design high-difficulty Data Interpretation & Logical Reasoning (DILR) caselets, complex Reading Comprehension with deep philosophical/analytical inferences (VARC), and rigorous Quantitative Aptitude (Algebra, Modern Math, Number Theory, Geometry).",
                patternRules = listOf(
                    "MCQs: +3 marks for correct, -1 mark penalty for incorrect",
                    "Non-MCQs (TITA): +3 marks for correct, 0 penalty for incorrect",
                    "66 questions total, 198 marks, 120 minutes (40 minutes sectional time limit)"
                ),
                stages = listOf(
                    StageConfig(
                        "cat_exam", "CAT Single Exam",
                        listOf(
                            PaperConfig(
                                "cat_paper", "CAT Composite",
                                durationMinutes = 120,
                                totalMarks = 198.0,
                                subjects = listOf(
                                    SubjectConfig("varc", "Verbal Ability & Reading Comprehension", listOf(SectionConfig("varc_sec", "VARC", 24, 24, 3.0, 1.0, QuestionType.PASSAGE_BASED))),
                                    SubjectConfig("dilr", "Data Interpretation & Logical Reasoning", listOf(SectionConfig("dilr_sec", "DILR", 20, 20, 3.0, 1.0, QuestionType.CASE_BASED))),
                                    SubjectConfig("qa", "Quantitative Ability", listOf(SectionConfig("qa_sec", "QA", 22, 22, 3.0, 0.0, QuestionType.NUMERICAL_VALUE, optionsCount = 0)))
                    )
                )
            )
        )
                )
            )
        )

        // 18. IBPS PO (Institute of Banking Personnel Selection)
        register(
            ExamConfig(
                examId = "ibps_po",
                examName = "IBPS PO",
                shortName = "IBPS PO",
                category = "Banking",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 5,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 20, mediumPercent = 50, hardPercent = 30),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.CASE_BASED, QuestionType.PASSAGE_BASED),
                aiSystemInstruction = "You are an examiner for IBPS PO Prelims. Formulate multi-attribute seating arrangement puzzles, Data Interpretation sets (pie charts, tables, missing DI), quadratic equations, approximation, and advanced English cloze tests.",
                patternRules = listOf(
                    "+1 mark for correct, -0.25 mark penalty for incorrect",
                    "100 questions (30 English, 35 Quant, 35 Reasoning), Total 100 marks, Duration 60 minutes",
                    "Strict sectional timing: 20 minutes per section"
                ),
                stages = listOf(
                    StageConfig(
                        "prelims", "Preliminary Exam",
                        listOf(
                            PaperConfig(
                                "po_prelims", "Prelims Paper",
                                durationMinutes = 60,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("english_language", "English Language", listOf(SectionConfig("eng_sec", "English", 30, 30, 1.0, 0.25, optionsCount = 5))),
                                    SubjectConfig("quantitative_aptitude", "Quantitative Aptitude", listOf(SectionConfig("qa_sec", "Quant", 35, 35, 1.0, 0.25, optionsCount = 5))),
                                    SubjectConfig("reasoning_ability", "Reasoning Ability", listOf(SectionConfig("reaz_sec", "Reasoning", 35, 35, 1.0, 0.25, optionsCount = 5)))
                    )
                )
            )
        )
                )
            )
        )

        // 19. IBPS Clerk
        register(
            ExamConfig(
                examId = "ibps_clerk",
                examName = "IBPS Clerk",
                shortName = "IBPS Clerk",
                category = "Banking",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 5,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 45, mediumPercent = 45, hardPercent = 10),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ),
                aiSystemInstruction = "You are an examiner for IBPS Clerk Prelims. Emphasize fast mental calculations, simplification, arithmetic word problems, standard blood relations, direction sense, syllogisms, and vocabulary.",
                patternRules = listOf(
                    "+1 mark for correct, -0.25 mark penalty for incorrect",
                    "100 questions (30 English, 35 Quant, 35 Reasoning), Total 100 marks, Duration 60 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "prelims", "Preliminary Exam",
                        listOf(
                            PaperConfig(
                                "clerk_prelims", "Prelims Paper",
                                durationMinutes = 60,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("english_language", "English Language", listOf(SectionConfig("eng_sec", "English", 30, 30, 1.0, 0.25, optionsCount = 5))),
                                    SubjectConfig("numerical_ability", "Numerical Ability", listOf(SectionConfig("num_sec", "Numerical", 35, 35, 1.0, 0.25, optionsCount = 5))),
                                    SubjectConfig("reasoning_ability", "Reasoning Ability", listOf(SectionConfig("reaz_sec", "Reasoning", 35, 35, 1.0, 0.25, optionsCount = 5)))
                    )
                )
            )
        )
                )
            )
        )

        // 20. SBI Clerk (Junior Associates)
        register(
            ExamConfig(
                examId = "sbi_clerk",
                examName = "SBI Clerk",
                shortName = "SBI Clerk",
                category = "Banking",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 5,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 40, mediumPercent = 50, hardPercent = 10),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ),
                aiSystemInstruction = "You are an examiner for SBI Clerk (Junior Associates). Focus on high-speed Quantitative Aptitude (simplification, number series, quadratic equations), Reasoning puzzles, and English comprehension.",
                patternRules = listOf(
                    "+1 mark for correct, -0.25 mark penalty for incorrect",
                    "100 questions, Total 100 marks, Duration 60 minutes (20 mins per section)"
                ),
                stages = listOf(
                    StageConfig(
                        "prelims", "Preliminary Exam",
                        listOf(
                            PaperConfig(
                                "sbi_clerk_prelims", "Prelims Paper",
                                durationMinutes = 60,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("english_language", "English Language", listOf(SectionConfig("eng_sec", "English", 30, 30, 1.0, 0.25, optionsCount = 5))),
                                    SubjectConfig("numerical_ability", "Numerical Ability", listOf(SectionConfig("num_sec", "Numerical", 35, 35, 1.0, 0.25, optionsCount = 5))),
                                    SubjectConfig("reasoning_ability", "Reasoning Ability", listOf(SectionConfig("reaz_sec", "Reasoning", 35, 35, 1.0, 0.25, optionsCount = 5)))
                    )
                )
            )
        )
                )
            )
        )

        // 21. SBI PO (Probationary Officers)
        register(
            ExamConfig(
                examId = "sbi_po",
                examName = "SBI PO",
                shortName = "SBI PO",
                category = "Banking",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 5,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 15, mediumPercent = 50, hardPercent = 35),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.CASE_BASED, QuestionType.PASSAGE_BASED),
                aiSystemInstruction = "You are an examiner for SBI PO. Frame intricate reasoning puzzles, novel-pattern critical reasoning, advanced data sufficiency, high-level DI sets, and rigorous English vocabulary in context.",
                patternRules = listOf(
                    "+1 mark for correct, -0.25 mark penalty for incorrect",
                    "100 questions (30 English, 35 Quant, 35 Reasoning), Total 100 marks, 60 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "prelims", "Preliminary Exam",
                        listOf(
                            PaperConfig(
                                "sbi_po_prelims", "Prelims Paper",
                                durationMinutes = 60,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("english_language", "English Language", listOf(SectionConfig("eng_sec", "English", 30, 30, 1.0, 0.25, optionsCount = 5))),
                                    SubjectConfig("quantitative_aptitude", "Quantitative Aptitude", listOf(SectionConfig("qa_sec", "Quant", 35, 35, 1.0, 0.25, optionsCount = 5))),
                                    SubjectConfig("reasoning_ability", "Reasoning Ability", listOf(SectionConfig("reaz_sec", "Reasoning", 35, 35, 1.0, 0.25, optionsCount = 5)))
                    )
                )
            )
        )
                )
            )
        )

        // 22. NDA (National Defence Academy - UPSC)
        register(
            ExamConfig(
                examId = "nda",
                examName = "NDA & NA",
                shortName = "NDA",
                category = "Defence",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 30, mediumPercent = 50, hardPercent = 20),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.ASSERTION_REASON, QuestionType.GRAPH),
                aiSystemInstruction = "You are an examiner for UPSC NDA & NA. Formulate Mathematics questions (Class 11-12 Trigonometry, Matrices, Calculus, Vectors, Probability) and General Ability Test (GAT) questions in English, Physics, Chemistry, History, Geography, and Current Events.",
                patternRules = listOf(
                    "Mathematics: +2.5 marks for correct, -0.83 penalty for incorrect (120 Qs, 300 marks, 150 mins)",
                    "GAT: +4 marks for correct, -1.33 penalty for incorrect (150 Qs, 600 marks, 150 mins)",
                    "Total 900 marks written examination"
                ),
                stages = listOf(
                    StageConfig(
                        "written", "Written Examination",
                        listOf(
                            PaperConfig(
                                "nda_math", "Mathematics",
                                durationMinutes = 150,
                                totalMarks = 300.0,
                                subjects = listOf(
                                    SubjectConfig("mathematics", "Mathematics", listOf(SectionConfig("math_sec", "Math", 120, 120, 2.5, 0.83)))
                                )
                            ),
                            PaperConfig(
                                "nda_gat", "General Ability Test (GAT)",
                                durationMinutes = 150,
                                totalMarks = 600.0,
                                subjects = listOf(
                                    SubjectConfig("english", "English", listOf(SectionConfig("eng_sec", "English", 50, 50, 4.0, 1.33))),
                                    SubjectConfig("general_knowledge", "General Knowledge", listOf(SectionConfig("gk_sec", "GK", 100, 100, 4.0, 1.33)))
                    )
                )
            )
        )
                )
            )
        )

        // 22b. CDS (Combined Defence Services - UPSC)
        register(
            ExamConfig(
                examId = "cds",
                examName = "CDS",
                shortName = "CDS",
                category = "Defence",
                language = "Bilingual (English & Hindi)",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 30, mediumPercent = 50, hardPercent = 20),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.ASSERTION_REASON, QuestionType.STATEMENT_I_II),
                aiSystemInstruction = "You are an examiner for UPSC Combined Defence Services (CDS). Formulate questions for English, General Knowledge, and Elementary Mathematics according to UPSC CDS standards.",
                patternRules = listOf(
                    "English: +0.83 marks for correct, -0.27 penalty for incorrect (120 Qs, 100 marks, 120 mins)",
                    "General Knowledge: +0.83 marks for correct, -0.27 penalty for incorrect (120 Qs, 100 marks, 120 mins)",
                    "Elementary Mathematics: +1.0 mark for correct, -0.33 penalty for incorrect (100 Qs, 100 marks, 120 mins)"
                ),
                stages = listOf(
                    StageConfig(
                        "written", "Written Examination",
                        listOf(
                            PaperConfig(
                                "cds_english", "English",
                                durationMinutes = 120,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("english", "English", listOf(SectionConfig("cds_eng_sec", "English", 120, 120, 100.0 / 120.0, 100.0 / 360.0)))
                                )
                            ),
                            PaperConfig(
                                "cds_gk", "General Knowledge",
                                durationMinutes = 120,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("general_knowledge", "General Knowledge", listOf(SectionConfig("cds_gk_sec", "General Knowledge", 120, 120, 100.0 / 120.0, 100.0 / 360.0)))
                                )
                            ),
                            PaperConfig(
                                "cds_math", "Elementary Mathematics",
                                durationMinutes = 120,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig("mathematics", "Elementary Mathematics", listOf(SectionConfig("cds_math_sec", "Mathematics", 100, 100, 1.0, 0.33)))
                                )
                            )
                        )
                    )
                )
            )
        )

        // 23. GATE (Graduate Aptitude Test in Engineering)
        register(
            ExamConfig(
                examId = "gate",
                examName = "GATE",
                shortName = "GATE",
                category = "Engineering",
                language = "English",
                defaultOptionCount = 4,
                verified = true,
                difficultyRatio = DifficultyRatio(easyPercent = 20, mediumPercent = 45, hardPercent = 35),
                supportedQuestionTypes = listOf(QuestionType.DIRECT_MCQ, QuestionType.MCQ_MULTIPLE, QuestionType.NUMERICAL_VALUE, QuestionType.GRAPH),
                aiSystemInstruction = "You are the Chief Examiner for GATE. Frame rigorous engineering and general aptitude problems. Include Multiple Choice (MCQ), Multiple Select (MSQ), and Numerical Answer Type (NAT) problems requiring numerical precision.",
                patternRules = listOf(
                    "1-mark MCQ: +1 correct, -0.33 penalty. 2-mark MCQ: +2 correct, -0.66 penalty.",
                    "MSQ and NAT: NO negative marking (0 penalty).",
                    "65 questions total, 100 marks, Duration 180 minutes"
                ),
                stages = listOf(
                    StageConfig(
                        "gate_cbt", "GATE Online Exam",
                        listOf(
                            PaperConfig(
                                "gate_paper", "GATE Engineering Paper",
                                durationMinutes = 180,
                                totalMarks = 100.0,
                                subjects = listOf(
                                    SubjectConfig(
                                        "general_aptitude", "General Aptitude",
                                        listOf(
                                            SectionConfig("ga_1m", "General Aptitude (1-Mark)", 5, 5, 1.0, 0.33, QuestionType.MCQ_SINGLE, optionsCount = 4),
                                            SectionConfig("ga_2m", "General Aptitude (2-Mark)", 5, 5, 2.0, 0.66, QuestionType.MCQ_SINGLE, optionsCount = 4)
                                        )
                                    ),
                                    SubjectConfig(
                                        "technical_engineering", "Engineering Mathematics & Technical Core",
                                        listOf(
                                            SectionConfig("tech_1m", "Technical Core (1-Mark)", 25, 25, 1.0, 0.0, QuestionType.NUMERICAL_VALUE, optionsCount = 0),
                                            SectionConfig("tech_2m", "Technical Core (2-Mark)", 30, 30, 2.0, 0.0, QuestionType.NUMERICAL_VALUE, optionsCount = 0)
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    fun register(config: ExamConfig) {
        defaultExams[config.examId] = config
    }

    fun getAllExams(): List<ExamConfig> = defaultExams.values.toList()

    fun getActiveExams(): List<ExamConfig> = defaultExams.values.filter { it.active }

    fun getExamById(examId: String): ExamConfig? = defaultExams[examId]

    fun findExamByNameOrShortName(query: String): ExamConfig? {
        val q = query.trim().lowercase()
        val exact = defaultExams.values.find {
            it.examName.lowercase() == q ||
            it.shortName.lowercase() == q ||
            it.examId.lowercase() == q
        }
        if (exact != null) return exact

        return defaultExams.values.find {
            it.examName.lowercase().contains(q) ||
            q.contains(it.examName.lowercase()) ||
            q.contains(it.shortName.lowercase())
        }
    }

    fun getDifficultyRatioForExam(examName: String): DifficultyRatio {
        val config = findExamByNameOrShortName(examName)
        return config?.difficultyRatio ?: DifficultyRatio(40, 40, 20)
    }

    fun getSupportedQuestionTypesForExam(examName: String): List<QuestionType> {
        val config = findExamByNameOrShortName(examName)
        return config?.supportedQuestionTypes ?: listOf(QuestionType.MCQ_SINGLE)
    }

    fun getAiSystemInstructionForExam(examName: String): String {
        val config = findExamByNameOrShortName(examName)
        return config?.aiSystemInstruction?.takeIf { it.isNotBlank() }
            ?: "You are an expert academic examiner and question generator for $examName."
    }

    fun getPatternRulesForExam(examName: String): List<String> {
        val config = findExamByNameOrShortName(examName)
        return config?.patternRules ?: emptyList()
    }

    fun getSubjectsForExamName(examName: String): List<String> {
        val config = findExamByNameOrShortName(examName)
        if (config != null) {
            val subjects = config.stages.flatMap { stage ->
                stage.papers.flatMap { paper ->
                    paper.subjects.map { it.subjectName }
                }
            }.distinct()
            if (subjects.isNotEmpty()) return subjects
        }
        return listOf("Physics", "Chemistry", "Biology", "Mathematics")
    }

    fun getDefaultQuestionsCountForExam(examName: String): Int {
        val config = findExamByNameOrShortName(examName)
        if (config != null) {
            val paper = config.stages.firstOrNull()?.papers?.firstOrNull()
            if (paper != null) {
                val sum = paper.subjects.sumOf { subj ->
                    subj.sections.sumOf { it.compulsoryQuestions }
                }
                if (sum > 0) return sum
            }
        }
        return 180
    }

    fun getDefaultDurationMinutesForExam(examName: String): Int {
        val config = findExamByNameOrShortName(examName)
        return config?.stages?.firstOrNull()?.papers?.firstOrNull()?.durationMinutes ?: 180
    }

    fun getMarkingSchemeForExam(examName: String): Pair<Double, Double> {
        val config = findExamByNameOrShortName(examName)
        val section = config?.stages?.firstOrNull()?.papers?.firstOrNull()?.subjects?.firstOrNull()?.sections?.firstOrNull()
        return if (section != null) {
            Pair(section.positiveMarks, section.negativeMarks)
        } else {
            Pair(4.0, 1.0)
        }
    }

    fun getMarkingSchemeForQuestion(
        examName: String,
        sectionIdOrName: String? = null,
        subjectName: String? = null,
        questionTypeStr: String? = null
    ): Pair<Double, Double> {
        val config = findExamByNameOrShortName(examName) ?: return Pair(4.0, 1.0)
        val secQuery = sectionIdOrName?.trim()?.lowercase() ?: ""
        val subQuery = subjectName?.trim()?.lowercase() ?: ""
        val typeQuery = questionTypeStr?.trim()?.uppercase() ?: ""

        // Specific handling for JEE Advanced section/question-type resolution
        if (config.examId == "jee_advanced" || config.shortName.equals("JEE Adv", ignoreCase = true) || config.examName.contains("Advanced", ignoreCase = true)) {
            val isMulti = secQuery.contains("multi") || typeQuery == "MCQ_MULTIPLE" || typeQuery == "MULTIPLE"
            val isNum = secQuery.contains("num") || secQuery.contains("nvq") ||
                    typeQuery == "NUMERICAL" || typeQuery == "NUMERICAL_VALUE" || typeQuery == "TITA"
            val isSingle = secQuery.contains("single") || typeQuery == "MCQ_SINGLE" || typeQuery == "MCQ" || typeQuery == "DIRECT_MCQ"

            if (isMulti) return Pair(4.0, 2.0)
            if (isNum) return Pair(3.0, 0.0)
            if (isSingle) return Pair(3.0, 1.0)
        }

        // Specific handling for NDA & NA section/paper resolution
        if (config.shortName.equals("NDA", ignoreCase = true) || config.examId.equals("nda", ignoreCase = true)) {
            val isMath = secQuery.contains("math") || subQuery.contains("math")
            val isGat = secQuery.contains("gat") || secQuery.contains("eng") || secQuery.contains("gk") ||
                    subQuery.contains("gat") || subQuery.contains("eng") || subQuery.contains("gk") ||
                    subQuery.contains("aware") || subQuery.contains("knowl") || subQuery.contains("science") ||
                    subQuery.contains("history") || subQuery.contains("geograph")

            if (isMath && !isGat) {
                return Pair(2.5, 0.83)
            }
            if (isGat) {
                return Pair(4.0, 1.33)
            }
        }

        // Specific handling for CDS section/paper resolution
        if (config.shortName.equals("CDS", ignoreCase = true) || config.examId.equals("cds", ignoreCase = true)) {
            val isMath = secQuery.contains("math") || subQuery.contains("math")
            if (isMath) {
                return Pair(1.0, 0.33)
            } else {
                return Pair(0.83, 0.27)
            }
        }

        // Specific handling for GATE section/question-type resolution
        if (config.shortName.equals("GATE", ignoreCase = true) || config.examId.equals("gate", ignoreCase = true)) {
            val is2Mark = secQuery.contains("2m") || secQuery.contains("2-mark") || secQuery.contains("2_mark") ||
                    secQuery.contains("2 mark") || subQuery.contains("2m")
            val isTechOrNumerical = secQuery.contains("tech") || typeQuery == "NUMERICAL_VALUE" ||
                    typeQuery == "NUMERICAL" || typeQuery == "TITA" || subQuery.contains("tech") ||
                    subQuery.contains("engineering")

            if (isTechOrNumerical) {
                return if (is2Mark) Pair(2.0, 0.0) else Pair(1.0, 0.0)
            } else {
                return if (is2Mark) Pair(2.0, 0.66) else Pair(1.0, 0.33)
            }
        }

        // Specific handling for SSC MTS section/session resolution
        if (config.shortName.equals("MTS", ignoreCase = true) || config.examId.equals("ssc_mts", ignoreCase = true) || config.examName.contains("MTS", ignoreCase = true)) {
            val isSession1 = secQuery.contains("session_1") || secQuery.contains("math") || secQuery.contains("reason") ||
                    secQuery == "math_sec" || secQuery == "reaz_sec" ||
                    subQuery.contains("session_1") || subQuery.contains("math") || subQuery.contains("reason") ||
                    subQuery.contains("numerical")
            val isSession2 = secQuery.contains("session_2") || secQuery.contains("ga") || secQuery.contains("eng") ||
                    secQuery == "ga_sec" || secQuery == "eng_sec" ||
                    subQuery.contains("session_2") || subQuery.contains("general") || subQuery.contains("awareness") ||
                    subQuery.contains("english")

            if (isSession2 && !isSession1) {
                return Pair(3.0, 1.0)
            }
            if (isSession1) {
                return Pair(3.0, 0.0)
            }
        }

        // Generic section match across configured stages and papers
        if (secQuery.isNotBlank() && secQuery != "section_a" && secQuery != "section_b") {
            for (stage in config.stages) {
                for (paper in stage.papers) {
                    for (subject in paper.subjects) {
                        for (sec in subject.sections) {
                            if (sec.sectionId.equals(secQuery, ignoreCase = true) ||
                                sec.sectionName.equals(sectionIdOrName?.trim(), ignoreCase = true) ||
                                sec.sectionName.lowercase() == secQuery
                            ) {
                                return Pair(sec.positiveMarks, sec.negativeMarks)
                            }
                        }
                    }
                }
            }
        }

        // Generic subject match
        if (subQuery.isNotBlank()) {
            for (stage in config.stages) {
                for (paper in stage.papers) {
                    for (subject in paper.subjects) {
                        if (subject.subjectId.equals(subQuery, ignoreCase = true) ||
                            subject.subjectName.equals(subjectName?.trim(), ignoreCase = true) ||
                            subject.subjectName.lowercase().contains(subQuery) ||
                            subQuery.contains(subject.subjectName.lowercase())
                        ) {
                            val sec = subject.sections.firstOrNull()
                            if (sec != null) return Pair(sec.positiveMarks, sec.negativeMarks)
                        }
                    }
                }
            }
        }

        return getMarkingSchemeForExam(examName)
    }

    fun hasMultipleMarkingSchemes(examName: String): Boolean {
        val config = findExamByNameOrShortName(examName) ?: return false
        if (config.shortName.equals("NDA", ignoreCase = true) || config.examId.equals("nda", ignoreCase = true) ||
            config.shortName.equals("CDS", ignoreCase = true) || config.examId.equals("cds", ignoreCase = true) ||
            config.shortName.equals("GATE", ignoreCase = true) || config.examId.equals("gate", ignoreCase = true) ||
            config.shortName.equals("MTS", ignoreCase = true) || config.examId.equals("ssc_mts", ignoreCase = true) ||
            config.examId == "jee_advanced" || config.shortName.equals("JEE Adv", ignoreCase = true) || config.examName.contains("Advanced", ignoreCase = true)
        ) {
            return true
        }

        val allSections = config.stages.flatMap { it.papers.flatMap { p -> p.subjects.flatMap { s -> s.sections } } }
        if (allSections.size > 1) {
            val firstPos = allSections.first().positiveMarks
            val firstNeg = allSections.first().negativeMarks
            return allSections.any { it.positiveMarks != firstPos || it.negativeMarks != firstNeg }
        }
        return false
    }

    fun getMaximumAttemptQuestionsCountForExam(examName: String): Int? {
        val config = findExamByNameOrShortName(examName) ?: return null
        val paper = config.stages.firstOrNull()?.papers?.firstOrNull() ?: return null
        val totalComp = paper.subjects.sumOf { subj -> subj.sections.sumOf { it.compulsoryQuestions } }
        val totalPaper = paper.subjects.sumOf { subj -> subj.sections.sumOf { it.totalQuestions } }
        return if (totalComp in 1 until totalPaper) totalComp else null
    }

    fun getMaximumAttemptQuestionsCountForSubject(examName: String, subjectNameOrId: String): Int? {
        val config = findExamByNameOrShortName(examName) ?: return null
        val clean = subjectNameOrId.trim()
        if (clean.equals("biology", ignoreCase = true) && (config.shortName.equals("NEET", ignoreCase = true) || config.examId.contains("neet", ignoreCase = true))) {
            return 90
        }
        val allPapers = config.stages.flatMap { it.papers }
        val subject = allPapers.flatMap { it.subjects }.find {
            it.subjectId.equals(clean, ignoreCase = true) ||
            it.subjectName.equals(clean, ignoreCase = true) ||
            clean.contains(it.subjectId, ignoreCase = true) ||
            clean.contains(it.subjectName, ignoreCase = true)
        } ?: return null
        val totalComp = subject.sections.sumOf { it.compulsoryQuestions }
        val totalPaper = subject.sections.sumOf { it.totalQuestions }
        return if (totalComp in 1 until totalPaper) totalComp else null
    }

    fun getMaximumAttemptQuestionsCountForSection(
        examName: String,
        sectionNameOrId: String,
        subjectName: String? = null
    ): Int? {
        val config = findExamByNameOrShortName(examName) ?: return null
        val secClean = sectionNameOrId.trim().lowercase()
        val subClean = subjectName?.trim()?.lowercase() ?: ""

        val allPapers = config.stages.flatMap { it.papers }
        for (paper in allPapers) {
            for (subject in paper.subjects) {
                if (subClean.isNotEmpty() &&
                    !subject.subjectId.equals(subClean, ignoreCase = true) &&
                    !subject.subjectName.equals(subClean, ignoreCase = true) &&
                    !subClean.contains(subject.subjectId.lowercase()) &&
                    !subject.subjectName.lowercase().contains(subClean)
                ) {
                    continue
                }
                for (sec in subject.sections) {
                    val idMatch = sec.sectionId.equals(secClean, ignoreCase = true)
                    val nameMatch = sec.sectionName.equals(secClean, ignoreCase = true)
                    val isSecB = (secClean == "section_b" || secClean == "section b") &&
                            (sec.sectionId.contains("sec_b") || sec.sectionName.contains("Section B"))
                    val isSecA = (secClean == "section_a" || secClean == "section a") &&
                            (sec.sectionId.contains("sec_a") || sec.sectionName.contains("Section A"))

                    if (idMatch || nameMatch || isSecB || isSecA) {
                        if (sec.compulsoryQuestions in 1 until sec.totalQuestions) {
                            return sec.compulsoryQuestions
                        }
                    }
                }
            }
        }
        // Fallback for Section B when subject is generic or not matched
        if (secClean == "section_b" || secClean.contains("section b")) {
            val isNeet = config.shortName.equals("NEET", ignoreCase = true) || config.examId.contains("neet", ignoreCase = true)
            if (isNeet) return 10
            val isJee = config.shortName.equals("JEE", ignoreCase = true) || config.examId.contains("jee", ignoreCase = true)
            if (isJee) return 5
        }
        return null
    }

    fun updateExamStatus(examId: String, active: Boolean) {
        defaultExams[examId]?.let {
            defaultExams[examId] = it.copy(active = active)
        }
    }

    fun getLanguageForExam(examName: String): String {
        val config = findExamByNameOrShortName(examName)
        return config?.language ?: "Bilingual (English & Hindi)"
    }

    fun getDefaultOptionCountForExam(examName: String): Int {
        val config = findExamByNameOrShortName(examName)
        return config?.defaultOptionCount ?: 4
    }

    fun isExamVerified(examId: String): Boolean {
        return defaultExams[examId]?.verified ?: false
    }

    fun getExamDisplaySubtitle(config: ExamConfig): String {
        val allPapers = config.stages.flatMap { it.papers }
        val paper = allPapers.firstOrNull()
        val totalCompQs = allPapers.sumOf { p -> p.subjects.sumOf { s -> s.sections.sumOf { it.compulsoryQuestions } } }
        val totalPaperQs = allPapers.sumOf { p -> p.subjects.sumOf { s -> s.sections.sumOf { it.totalQuestions } } }
        val totalMarks = allPapers.sumOf { it.totalMarks }
        val duration = allPapers.sumOf { it.durationMinutes }

        val firstSec = paper?.subjects?.firstOrNull()?.sections?.firstOrNull()
        val pos = firstSec?.positiveMarks ?: 4.0
        val neg = firstSec?.negativeMarks ?: 1.0

        val qText = if (totalCompQs < totalPaperQs) "$totalCompQs/$totalPaperQs Qs" else "$totalCompQs Qs"
        val marksText = if (totalMarks == totalMarks.toLong().toDouble()) "${totalMarks.toLong()} Marks" else "$totalMarks Marks"
        val durationText = "${duration} Mins"
        val posText = if (pos == pos.toLong().toDouble()) pos.toLong().toString() else String.format(java.util.Locale.US, "%.2f", pos).trimEnd('0').trimEnd('.')
        val negText = if (neg == neg.toLong().toDouble()) neg.toLong().toString() else String.format(java.util.Locale.US, "%.2f", neg).trimEnd('0').trimEnd('.')
        val markingText = if (neg == 0.0) "(+$posText, 0 -ve)" else "(+$posText, -$negText)"

        return "$qText • $marksText • $durationText $markingText"
    }

    fun getExamDisplaySubtitleByName(examName: String): String {
        val config = findExamByNameOrShortName(examName)
        return if (config != null) getExamDisplaySubtitle(config) else "Pattern from Official Syllabus"
    }
}
